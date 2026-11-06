package org.lpu.dev.codes.helpdesk.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.lpu.dev.codes.helpdesk.config.GateAttendanceProperties;
import org.lpu.dev.codes.helpdesk.dto.DirectoryPhotoRow;
import org.lpu.dev.codes.helpdesk.dto.MonitorSnapshotResponse;
import org.lpu.dev.codes.helpdesk.dto.QueueSnapshotResponse;
import org.lpu.dev.codes.helpdesk.dto.TicketResponse;
import org.lpu.dev.codes.helpdesk.model.PendingRequesterEmail;
import org.lpu.dev.codes.helpdesk.model.Ticket;
import org.lpu.dev.codes.helpdesk.model.User;
import org.lpu.dev.codes.helpdesk.repository.EmployeeRepository;
import org.lpu.dev.codes.helpdesk.repository.StudentRepository;
import org.lpu.dev.codes.helpdesk.repository.TicketRepository;
import org.lpu.dev.codes.helpdesk.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MonitorService {

    private static final Logger log = LoggerFactory.getLogger(MonitorService.class);
    private static final long PHOTO_CACHE_TTL_MS = 120_000;

    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final EmployeeRepository employeeRepository;
    private final GateAttendanceProperties gateAttendanceProperties;
    private final ConcurrentHashMap<String, PhotoCacheEntry> photoCache = new ConcurrentHashMap<>();
    private volatile long photoLookupBlockedUntil = 0;

    public MonitorService(
            TicketRepository ticketRepository,
            UserRepository userRepository,
            StudentRepository studentRepository,
            EmployeeRepository employeeRepository,
            GateAttendanceProperties gateAttendanceProperties
    ) {
        this.ticketRepository = ticketRepository;
        this.userRepository = userRepository;
        this.studentRepository = studentRepository;
        this.employeeRepository = employeeRepository;
        this.gateAttendanceProperties = gateAttendanceProperties;
    }

    /**
     * Lean snapshot for the TV board — waiting + now-serving + recent online only.
     * Avoids transfer-request work used by the admin queue page.
     */
    @Transactional(readOnly = true)
    public MonitorSnapshotResponse getSnapshot(int recentLimit) {
        List<Ticket> waiting = ticketRepository.findWaitingOnsiteOrderByQueueNumber();
        List<Ticket> serving = ticketRepository.findServingOnsite();
        List<Ticket> recent = ticketRepository.findRecentOnlineOrderByCreatedAtDesc(recentLimit);

        Map<Long, User> adminsById = userRepository.findByIdIn(
                serving.stream().map(Ticket::getAssignedAdminId).distinct().toList()
        );
        Map<Long, String> photos = photosFor(waiting, serving, recent);

        List<QueueSnapshotResponse.NowServingEntry> nowServing = serving.stream()
                .map(t -> {
                    User admin = adminsById.get(t.getAssignedAdminId());
                    String adminName = admin != null ? admin.getName() : "Unknown";
                    return new QueueSnapshotResponse.NowServingEntry(
                            t.getAssignedAdminId(),
                            adminName,
                            withPhoto(TicketResponse.from(t, adminName), photos.get(t.getId()))
                    );
                })
                .toList();

        List<TicketResponse> waitingResponses = waiting.stream()
                .map(t -> withPhoto(TicketResponse.from(t), photos.get(t.getId())))
                .toList();
        List<TicketResponse> recentResponses = recent.stream()
                .map(t -> withPhoto(TicketResponse.from(t), photos.get(t.getId())))
                .toList();

        return new MonitorSnapshotResponse(nowServing, waitingResponses, recentResponses);
    }

    private static TicketResponse withPhoto(TicketResponse response, String photo) {
        return response.withRequesterPhoto(photo);
    }

    private Map<Long, String> photosFor(List<Ticket> waiting, List<Ticket> serving, List<Ticket> recent) {
        List<Ticket> tickets = new ArrayList<>(waiting.size() + serving.size() + recent.size());
        tickets.addAll(waiting);
        tickets.addAll(serving);
        tickets.addAll(recent);
        if (tickets.isEmpty() || System.currentTimeMillis() < photoLookupBlockedUntil) {
            return Map.of();
        }
        try {
            Map<Long, String> photos = resolvePhotos(tickets);
            photoLookupBlockedUntil = 0;
            return photos;
        } catch (RuntimeException ex) {
            photoLookupBlockedUntil = System.currentTimeMillis() + 30_000;
            log.warn("Monitor photo lookup failed: {}", ex.toString());
            return Map.of();
        }
    }

    private Map<Long, String> resolvePhotos(List<Ticket> tickets) {
        Map<Long, String> resolved = new HashMap<>();
        List<Ticket> misses = new ArrayList<>();
        for (Ticket ticket : tickets) {
            String key = cacheKey(ticket);
            if (key == null) {
                continue;
            }
            String cached = readCache(key);
            if (cached == null) {
                misses.add(ticket);
                continue;
            }
            if (!cached.isEmpty()) {
                resolved.put(ticket.getId(), cached);
            }
        }
        if (misses.isEmpty()) {
            return resolved;
        }

        fillCache(misses);
        for (Ticket ticket : misses) {
            String key = cacheKey(ticket);
            if (key == null) {
                continue;
            }
            String cached = readCache(key);
            if (cached != null && !cached.isEmpty()) {
                resolved.put(ticket.getId(), cached);
            }
        }
        return resolved;
    }

    private void fillCache(List<Ticket> tickets) {
        Set<String> studentIds = new LinkedHashSet<>();
        Set<String> employeeIds = new LinkedHashSet<>();
        Set<String> eitherIds = new LinkedHashSet<>();
        Set<String> emails = new LinkedHashSet<>();
        for (Ticket ticket : tickets) {
            String key = cacheKey(ticket);
            if (key == null) {
                continue;
            }
            if (key.startsWith("STUDENT|")) {
                studentIds.add(key.substring("STUDENT|".length()));
            } else if (key.startsWith("EMPLOYEE|")) {
                employeeIds.add(key.substring("EMPLOYEE|".length()));
            } else if (key.startsWith("ID|")) {
                eitherIds.add(key.substring("ID|".length()));
            } else if (key.startsWith("EMAIL|")) {
                emails.add(key.substring("EMAIL|".length()));
            }
        }

        Set<String> studentQuery = new LinkedHashSet<>(studentIds);
        studentQuery.addAll(eitherIds);
        Set<String> employeeQuery = new LinkedHashSet<>(employeeIds);
        employeeQuery.addAll(eitherIds);

        Map<String, String> studentsById = indexByNumber(
                studentRepository.findPhotosByIdentifiers(studentQuery)
        );
        Map<String, String> employeesById = indexByNumber(
                employeeRepository.findPhotosByIdentifiers(employeeQuery)
        );
        Map<String, String> studentsByEmail = indexByEmail(studentRepository.findPhotosByEmails(emails));
        Map<String, String> employeesByEmail = indexByEmail(employeeRepository.findPhotosByEmails(emails));

        for (String id : studentIds) {
            storeCache("STUDENT|" + id, studentsById.get(id));
        }
        for (String id : employeeIds) {
            storeCache("EMPLOYEE|" + id, employeesById.get(id));
        }
        for (String id : eitherIds) {
            String photo = firstUrl(studentsById.get(id), employeesById.get(id));
            storeCache("ID|" + id, photo);
        }
        for (String email : emails) {
            String photo = firstUrl(studentsByEmail.get(email), employeesByEmail.get(email));
            storeCache("EMAIL|" + email, photo);
        }
    }

    private Map<String, String> indexByNumber(List<DirectoryPhotoRow> rows) {
        Map<String, String> indexed = new HashMap<>();
        for (DirectoryPhotoRow row : rows) {
            String url = gateAttendanceProperties.resolvePhotoUrl(row.photo());
            if (url == null) {
                continue;
            }
            putUrl(indexed, row.number(), url);
            putUrl(indexed, row.rfid(), url);
        }
        return indexed;
    }

    private Map<String, String> indexByEmail(List<DirectoryPhotoRow> rows) {
        Map<String, String> indexed = new HashMap<>();
        for (DirectoryPhotoRow row : rows) {
            String url = gateAttendanceProperties.resolvePhotoUrl(row.photo());
            if (url == null || row.email() == null || row.email().isBlank()) {
                continue;
            }
            putUrl(indexed, row.email().trim().toLowerCase(), url);
        }
        return indexed;
    }

    private static void putUrl(Map<String, String> indexed, String key, String url) {
        if (key == null || key.isBlank() || url == null || url.isBlank()) {
            return;
        }
        indexed.putIfAbsent(key.trim(), url);
    }

    private static String firstUrl(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback;
        }
        return null;
    }

    private String readCache(String key) {
        PhotoCacheEntry entry = photoCache.get(key);
        if (entry == null || entry.expiresAtMillis() <= System.currentTimeMillis()) {
            return null;
        }
        return entry.url();
    }

    private void storeCache(String key, String url) {
        String stored = url == null ? "" : url;
        photoCache.put(key, new PhotoCacheEntry(stored, System.currentTimeMillis() + PHOTO_CACHE_TTL_MS));
    }

    private static String cacheKey(Ticket ticket) {
        String number = blankToNull(ticket.getRequesterPersonNo());
        String type = blankToNull(ticket.getRequesterPersonType());
        if (number != null) {
            if ("STUDENT".equalsIgnoreCase(type) || "EMPLOYEE".equalsIgnoreCase(type)) {
                return type.toUpperCase() + "|" + number;
            }
            return "ID|" + number;
        }
        String email = directoryEmail(ticket);
        if (email != null) {
            return "EMAIL|" + email;
        }
        return null;
    }

    private static String directoryEmail(Ticket ticket) {
        String declared = blankToNull(ticket.getRequesterLpuEmail());
        if (declared != null) {
            return declared.toLowerCase();
        }
        String sender = blankToNull(ticket.getRequesterEmail());
        if (sender != null && !PendingRequesterEmail.isPending(sender)) {
            return sender.toLowerCase();
        }
        return null;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private record PhotoCacheEntry(String url, long expiresAtMillis) {
    }
}

package org.lpu.dev.codes.helpdesk.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.lpu.dev.codes.helpdesk.dto.AnalyticsCsmByAssigneeResponse;
import org.lpu.dev.codes.helpdesk.dto.AnalyticsSummaryResponse;
import org.lpu.dev.codes.helpdesk.dto.AnalyticsSummaryResponse.AssigneeLoad;
import org.lpu.dev.codes.helpdesk.dto.AnalyticsSummaryResponse.DayCsm;
import org.lpu.dev.codes.helpdesk.dto.AnalyticsSummaryResponse.DayVolume;
import org.lpu.dev.codes.helpdesk.dto.AnalyticsSummaryResponse.NamedCount;
import org.lpu.dev.codes.helpdesk.dto.AnalyticsSummaryResponse.QueueNow;
import org.lpu.dev.codes.helpdesk.dto.AnalyticsSummaryResponse.Totals;
import org.lpu.dev.codes.helpdesk.dto.AnalyticsTicketListResponse;
import org.lpu.dev.codes.helpdesk.dto.AnalyticsTicketListResponse.Item;
import org.lpu.dev.codes.helpdesk.model.CsmRating;
import org.lpu.dev.codes.helpdesk.model.Ticket;
import org.lpu.dev.codes.helpdesk.model.TicketChannel;
import org.lpu.dev.codes.helpdesk.model.TicketCsm;
import org.lpu.dev.codes.helpdesk.model.TicketStatus;
import org.lpu.dev.codes.helpdesk.model.User;
import org.lpu.dev.codes.helpdesk.repository.TicketCsmRepository;
import org.lpu.dev.codes.helpdesk.repository.TicketRepository;
import org.lpu.dev.codes.helpdesk.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AnalyticsService {

    private static final int LIST_LIMIT = 200;

    private final TicketRepository ticketRepository;
    private final TicketCsmRepository ticketCsmRepository;
    private final UserRepository userRepository;

    public AnalyticsService(
            TicketRepository ticketRepository,
            TicketCsmRepository ticketCsmRepository,
            UserRepository userRepository
    ) {
        this.ticketRepository = ticketRepository;
        this.ticketCsmRepository = ticketCsmRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public AnalyticsSummaryResponse summarize(Instant from, Instant to) {
        Instant rangeFrom = from != null ? from : Instant.now().minus(30, ChronoUnit.DAYS);
        Instant rangeTo = to != null ? to : Instant.now().plus(1, ChronoUnit.DAYS);

        long created = ticketRepository.countCreatedBetween(rangeFrom, rangeTo);
        long closed = ticketRepository.countClosedBetween(rangeFrom, rangeTo);
        long open = ticketRepository.countByStatus(TicketStatus.OPEN);
        long inProgress = ticketRepository.countByStatus(TicketStatus.IN_PROGRESS);
        long unassignedOpen = ticketRepository.countUnassignedOpen();
        Double avgResolve = ticketRepository.avgResolveHoursBetween(rangeFrom, rangeTo);
        if (avgResolve != null) {
            avgResolve = Math.round(avgResolve * 10.0) / 10.0;
        }

        Map<String, Long> csmByRating = new LinkedHashMap<>();
        csmByRating.put(CsmRating.SAD.name(), 0L);
        csmByRating.put(CsmRating.NEUTRAL.name(), 0L);
        csmByRating.put(CsmRating.HAPPY.name(), 0L);
        long csmCount = 0;
        for (Object[] row : ticketCsmRepository.countByRatingBetween(rangeFrom, rangeTo)) {
            CsmRating rating = (CsmRating) row[0];
            long count = ((Number) row[1]).longValue();
            csmByRating.put(rating.name(), count);
            csmCount += count;
        }
        Double happyPercent = csmCount == 0
                ? null
                : Math.round((csmByRating.get(CsmRating.HAPPY.name()) * 1000.0) / csmCount) / 10.0;

        Totals totals = new Totals(
                created,
                closed,
                open,
                inProgress,
                unassignedOpen,
                avgResolve,
                csmCount,
                csmByRating,
                happyPercent
        );

        List<NamedCount> byStatus = ticketRepository.countGroupByStatus().stream()
                .map(row -> {
                    TicketStatus status = (TicketStatus) row[0];
                    return new NamedCount(status.name(), statusLabel(status), ((Number) row[1]).longValue());
                })
                .toList();

        List<NamedCount> byChannel = ticketRepository.countCreatedGroupByChannel(rangeFrom, rangeTo).stream()
                .map(row -> {
                    TicketChannel channel = (TicketChannel) row[0];
                    return new NamedCount(
                            channel.name(),
                            channel == TicketChannel.ONSITE_RFID ? "Onsite" : "Online",
                            ((Number) row[1]).longValue()
                    );
                })
                .toList();

        List<NamedCount> byCategory = ticketRepository.countCreatedGroupByCategory(rangeFrom, rangeTo).stream()
                .map(row -> {
                    String code = (String) row[0];
                    return new NamedCount(code, CategoryLabelCache.labelFor(code), ((Number) row[1]).longValue());
                })
                .toList();

        Map<String, Long> createdByDay = toDayMap(ticketRepository.countCreatedByDay(rangeFrom, rangeTo));
        Map<String, Long> closedByDay = toDayMap(ticketRepository.countClosedByDay(rangeFrom, rangeTo));
        List<DayVolume> volumeByDay = fillDayRange(rangeFrom, rangeTo).stream()
                .map(day -> new DayVolume(
                        day,
                        createdByDay.getOrDefault(day, 0L),
                        closedByDay.getOrDefault(day, 0L)
                ))
                .toList();

        Map<String, long[]> csmDayBuckets = new TreeMap<>();
        for (String day : fillDayRange(rangeFrom, rangeTo)) {
            csmDayBuckets.put(day, new long[] {0, 0, 0});
        }
        for (Object[] row : ticketCsmRepository.countByDayAndRating(rangeFrom, rangeTo)) {
            String day = Objects.toString(row[0], null);
            String rating = Objects.toString(row[1], "");
            long count = ((Number) row[2]).longValue();
            long[] bucket = csmDayBuckets.computeIfAbsent(day, d -> new long[] {0, 0, 0});
            switch (rating) {
                case "SAD" -> bucket[0] = count;
                case "NEUTRAL" -> bucket[1] = count;
                case "HAPPY" -> bucket[2] = count;
                default -> {
                }
            }
        }
        List<DayCsm> csmByDay = csmDayBuckets.entrySet().stream()
                .map(e -> new DayCsm(e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2]))
                .toList();

        Map<Long, EnumMap<TicketStatus, Long>> assigneeMap = new LinkedHashMap<>();
        for (Object[] row : ticketRepository.assigneeLoad(rangeFrom, rangeTo)) {
            Long adminId = (Long) row[0];
            TicketStatus status = (TicketStatus) row[1];
            long count = ((Number) row[2]).longValue();
            assigneeMap
                    .computeIfAbsent(adminId, id -> new EnumMap<>(TicketStatus.class))
                    .put(status, count);
        }
        Map<Long, User> admins = userRepository.findByIdIn(new ArrayList<>(assigneeMap.keySet()));
        List<AssigneeLoad> byAssignee = assigneeMap.entrySet().stream()
                .map(e -> {
                    User admin = admins.get(e.getKey());
                    EnumMap<TicketStatus, Long> counts = e.getValue();
                    return new AssigneeLoad(
                            e.getKey(),
                            admin != null ? admin.getName() : ("Admin #" + e.getKey()),
                            counts.getOrDefault(TicketStatus.OPEN, 0L),
                            counts.getOrDefault(TicketStatus.IN_PROGRESS, 0L),
                            counts.getOrDefault(TicketStatus.CLOSED, 0L)
                    );
                })
                .sorted((a, b) -> Long.compare(
                        b.open() + b.inProgress() + b.closed(),
                        a.open() + a.inProgress() + a.closed()
                ))
                .toList();

        QueueNow queueToday = new QueueNow(
                ticketRepository.findWaitingOnsiteOrderByQueueNumber().size(),
                ticketRepository.findServingOnsite().size()
        );

        return new AnalyticsSummaryResponse(
                rangeFrom,
                rangeTo,
                totals,
                byStatus,
                byChannel,
                byCategory,
                volumeByDay,
                csmByDay,
                byAssignee,
                queueToday
        );
    }

    @Transactional(readOnly = true)
    public AnalyticsTicketListResponse assigneeTickets(Long adminId, Instant from, Instant to) {
        if (adminId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "adminId is required");
        }
        Instant rangeFrom = from != null ? from : Instant.now().minus(30, ChronoUnit.DAYS);
        Instant rangeTo = to != null ? to : Instant.now().plus(1, ChronoUnit.DAYS);

        User admin = userRepository.findById(adminId).orElse(null);
        String adminName = admin != null ? admin.getName() : ("Admin #" + adminId);

        // Fetch one extra to detect truncation
        List<Ticket> tickets = ticketRepository.findAssigneeTickets(
                adminId, rangeFrom, rangeTo, LIST_LIMIT + 1);
        boolean truncated = tickets.size() > LIST_LIMIT;
        if (truncated) {
            tickets = tickets.subList(0, LIST_LIMIT);
        }

        List<Item> items = tickets.stream()
                .map(t -> toItem(t, adminName, null))
                .toList();

        return new AnalyticsTicketListResponse(
                "Tickets — " + adminName,
                truncated,
                LIST_LIMIT,
                items
        );
    }

    @Transactional(readOnly = true)
    public AnalyticsTicketListResponse csmTickets(
            CsmRating rating,
            Instant from,
            Instant to,
            Long assignedAdminId
    ) {
        if (rating == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "rating is required");
        }
        Instant rangeFrom = from != null ? from : Instant.now().minus(30, ChronoUnit.DAYS);
        Instant rangeTo = to != null ? to : Instant.now().plus(1, ChronoUnit.DAYS);

        List<TicketCsm> csms = ticketCsmRepository.findByRatingBetween(
                rating, rangeFrom, rangeTo, assignedAdminId, LIST_LIMIT + 1);
        boolean truncated = csms.size() > LIST_LIMIT;
        if (truncated) {
            csms = csms.subList(0, LIST_LIMIT);
        }

        List<Long> ticketIds = csms.stream().map(TicketCsm::getTicketId).toList();
        Map<Long, Ticket> ticketsById = ticketRepository.findByIds(ticketIds).stream()
                .collect(Collectors.toMap(Ticket::getId, t -> t, (a, b) -> a, LinkedHashMap::new));

        List<Long> adminIds = ticketsById.values().stream()
                .map(Ticket::getAssignedAdminId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, User> admins = userRepository.findByIdIn(adminIds);

        List<Item> items = csms.stream()
                .map(csm -> {
                    Ticket ticket = ticketsById.get(csm.getTicketId());
                    if (ticket == null) {
                        return null;
                    }
                    String adminName = null;
                    if (ticket.getAssignedAdminId() != null) {
                        User admin = admins.get(ticket.getAssignedAdminId());
                        adminName = admin != null ? admin.getName() : ("Admin #" + ticket.getAssignedAdminId());
                    }
                    return toItem(ticket, adminName, csm);
                })
                .filter(Objects::nonNull)
                .toList();

        String face = switch (rating) {
            case HAPPY -> "Happy";
            case NEUTRAL -> "Neutral";
            case SAD -> "Sad";
        };
        String title = "CSM — " + face;
        if (assignedAdminId != null) {
            User admin = userRepository.findById(assignedAdminId).orElse(null);
            String adminName = admin != null ? admin.getName() : ("Admin #" + assignedAdminId);
            title = face + " — " + adminName;
        }

        return new AnalyticsTicketListResponse(
                title,
                truncated,
                LIST_LIMIT,
                items
        );
    }

    @Transactional(readOnly = true)
    public AnalyticsCsmByAssigneeResponse csmByAssignee(Instant from, Instant to) {
        Instant rangeFrom = from != null ? from : Instant.now().minus(30, ChronoUnit.DAYS);
        Instant rangeTo = to != null ? to : Instant.now().plus(1, ChronoUnit.DAYS);

        Map<Long, EnumMap<CsmRating, Long>> map = new LinkedHashMap<>();
        for (Object[] row : ticketCsmRepository.countByAssigneeAndRatingBetween(rangeFrom, rangeTo)) {
            Long adminId = (Long) row[0];
            CsmRating rating = (CsmRating) row[1];
            long count = ((Number) row[2]).longValue();
            map.computeIfAbsent(adminId, id -> new EnumMap<>(CsmRating.class)).put(rating, count);
        }

        Map<Long, User> admins = userRepository.findByIdIn(new ArrayList<>(map.keySet()));
        List<AnalyticsCsmByAssigneeResponse.AssigneeCsm> rows = map.entrySet().stream()
                .map(e -> {
                    User admin = admins.get(e.getKey());
                    EnumMap<CsmRating, Long> counts = e.getValue();
                    long sad = counts.getOrDefault(CsmRating.SAD, 0L);
                    long neutral = counts.getOrDefault(CsmRating.NEUTRAL, 0L);
                    long happy = counts.getOrDefault(CsmRating.HAPPY, 0L);
                    return new AnalyticsCsmByAssigneeResponse.AssigneeCsm(
                            e.getKey(),
                            admin != null ? admin.getName() : ("Admin #" + e.getKey()),
                            sad,
                            neutral,
                            happy,
                            sad + neutral + happy
                    );
                })
                .sorted((a, b) -> Long.compare(b.total(), a.total()))
                .toList();

        return new AnalyticsCsmByAssigneeResponse(rows);
    }

    private static Item toItem(Ticket ticket, String assignedAdminName, TicketCsm csm) {
        return new Item(
                ticket.getId(),
                ticket.getTicketNumber(),
                ticket.getSubject(),
                ticket.getStatus().name(),
                ticket.getCategory(),
                CategoryLabelCache.labelFor(ticket.getCategory()),
                ticket.getRequesterName(),
                ticket.getRequesterEmail(),
                ticket.getChannel().name(),
                ticket.getAssignedAdminId(),
                assignedAdminName,
                ticket.getCreatedAt(),
                ticket.getResolvedAt(),
                csm != null ? csm.getRating().name() : null,
                csm != null ? csm.getComment() : null,
                csm != null ? csm.getSubmittedAt() : null
        );
    }

    private static Map<String, Long> toDayMap(List<Object[]> rows) {
        return rows.stream().collect(Collectors.toMap(
                row -> Objects.toString(row[0], ""),
                row -> ((Number) row[1]).longValue(),
                Long::sum,
                TreeMap::new
        ));
    }

    private static List<String> fillDayRange(Instant from, Instant to) {
        LocalDate start = from.atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate end = to.atZone(ZoneOffset.UTC).toLocalDate();
        if (end.isBefore(start)) {
            return List.of();
        }
        // exclusive end day if to is start-of-day; include last full day otherwise
        if (to.atZone(ZoneOffset.UTC).toLocalTime().equals(java.time.LocalTime.MIDNIGHT)
                && !end.equals(start)) {
            end = end.minusDays(1);
        }
        List<String> days = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            days.add(d.toString());
            if (days.size() > 400) {
                break;
            }
        }
        return days;
    }

    private static String statusLabel(TicketStatus status) {
        return switch (status) {
            case OPEN -> "Open";
            case IN_PROGRESS -> "In Progress";
            case RESOLVED -> "Resolved";
            case CLOSED -> "Closed";
        };
    }
}

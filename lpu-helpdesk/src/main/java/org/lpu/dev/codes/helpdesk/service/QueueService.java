package org.lpu.dev.codes.helpdesk.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lpu.dev.codes.helpdesk.dto.QueueSnapshotResponse;
import org.lpu.dev.codes.helpdesk.dto.QueueTransferResponse;
import org.lpu.dev.codes.helpdesk.dto.TicketResponse;
import org.lpu.dev.codes.helpdesk.dto.WalkInTicketRequest;
import org.lpu.dev.codes.helpdesk.model.PendingRequesterEmail;
import org.lpu.dev.codes.helpdesk.model.QueueTransferRequest;
import org.lpu.dev.codes.helpdesk.model.QueueTransferStatus;
import org.lpu.dev.codes.helpdesk.model.Role;
import org.lpu.dev.codes.helpdesk.model.Ticket;
import org.lpu.dev.codes.helpdesk.model.TicketCategoryDefinition;
import org.lpu.dev.codes.helpdesk.model.TicketChannel;
import org.lpu.dev.codes.helpdesk.model.TicketStatus;
import org.lpu.dev.codes.helpdesk.model.User;
import org.lpu.dev.codes.helpdesk.repository.QueueCounterRepository;
import org.lpu.dev.codes.helpdesk.repository.QueueTransferRequestRepository;
import org.lpu.dev.codes.helpdesk.repository.TicketRepository;
import org.lpu.dev.codes.helpdesk.repository.UserRepository;
import org.lpu.dev.codes.helpdesk.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Onsite (walk-in / RFID kiosk) queueing. Each admin has their own
 * "now serving" slot — claiming a ticket assigns it to that admin.
 * Transfers require the target admin to be active and to approve first.
 */
@Service
public class QueueService {

    private static final Logger log = LogManager.getLogger(QueueService.class);
    private static final Set<Role> ADMIN_ROLES = EnumSet.of(Role.ADMIN, Role.SUPER_ADMIN);

    private final TicketRepository ticketRepository;
    private final QueueCounterRepository queueCounterRepository;
    private final QueueTransferRequestRepository transferRequestRepository;
    private final UserRepository userRepository;
    private final TicketCategoryService ticketCategoryService;
    private final TicketCsmService ticketCsmService;
    private final TicketThreadEmailService ticketThreadEmailService;

    public QueueService(
            TicketRepository ticketRepository,
            QueueCounterRepository queueCounterRepository,
            QueueTransferRequestRepository transferRequestRepository,
            UserRepository userRepository,
            TicketCategoryService ticketCategoryService,
            TicketCsmService ticketCsmService,
            TicketThreadEmailService ticketThreadEmailService
    ) {
        this.ticketRepository = ticketRepository;
        this.queueCounterRepository = queueCounterRepository;
        this.transferRequestRepository = transferRequestRepository;
        this.userRepository = userRepository;
        this.ticketCategoryService = ticketCategoryService;
        this.ticketCsmService = ticketCsmService;
        this.ticketThreadEmailService = ticketThreadEmailService;
    }

    @Transactional
    public Ticket createWalkInTicket(WalkInTicketRequest request) {
        TicketCategoryDefinition category = ticketCategoryService.requireActive(request.category());
        int queueNumber = queueCounterRepository.nextNumberForToday();
        String personType = blankToNull(request.personType());
        String personNo = blankToNull(request.personNo());
        if (personType != null) {
            personType = personType.toUpperCase();
        }
        String email = resolveRequesterEmail(request.email(), personType, personNo);
        ticketCsmService.requireNoPendingForPerson(email, personType, personNo);

        Ticket ticket = new Ticket();
        ticket.setRequesterEmail(email);
        ticket.setRequesterName(request.name().trim());
        if (!PendingRequesterEmail.isPending(email)) {
            userRepository.findUserByEmail(email).ifPresent(user -> ticket.setRequesterUserId(user.getId()));
        }
        ticket.setCategory(category.getCode());
        ticket.setSubject(request.subject().trim());
        ticket.setDescription(
                request.description() != null && !request.description().isBlank()
                        ? request.description().trim()
                        : "Walk-in request logged at the MISD helpdesk counter."
        );
        ticket.setStatus(TicketStatus.OPEN);
        ticket.setChannel(TicketChannel.ONSITE_RFID);
        ticket.setQueueNumber(queueNumber);
        if (personType != null) {
            ticket.setRequesterPersonType(personType);
        }
        if (personNo != null) {
            ticket.setRequesterPersonNo(personNo);
        }

        ticketRepository.persist(ticket);
        String datePart = LocalDate.now().toString().replace("-", "");
        ticket.setTicketNumber("Q-%s-%04d".formatted(datePart, queueNumber));
        Ticket saved = ticketRepository.save(ticket);
        log.info("Walk-in ticket created queueNumber={} ticketNumber={}", queueNumber, saved.getTicketNumber());
        return saved;
    }

    @Transactional
    public Ticket callNext(AuthenticatedUser actingAdmin) {
        requireNotAlreadyServing(actingAdmin.getId());

        Ticket next = ticketRepository.lockOldestWaitingOnsite()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "The queue is empty"));

        if (transferRequestRepository.existsPendingForTicket(next.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "The next ticket has a pending transfer — wait for approval or cancel it first"
            );
        }

        return beginServing(next, actingAdmin.getId());
    }

    /** Pick a specific waiting ticket and assign it to the acting admin. */
    @Transactional
    public Ticket claim(AuthenticatedUser actingAdmin, Long ticketId) {
        requireNotAlreadyServing(actingAdmin.getId());

        if (transferRequestRepository.existsPendingForTicket(ticketId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This ticket has a pending transfer — wait for approval or cancel it first"
            );
        }

        Ticket ticket = ticketRepository.lockWaitingOnsiteById(ticketId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "That ticket is no longer waiting — it may have been claimed already"
                ));

        return beginServing(ticket, actingAdmin.getId());
    }

    /**
     * Request a transfer to another active admin. The transfer only completes
     * after that admin approves.
     */
    @Transactional
    public QueueTransferResponse requestTransfer(AuthenticatedUser actingAdmin, Long ticketId, Long targetAdminId) {
        User target = requireActiveAdmin(targetAdminId, "Target admin is required");
        if (target.getId().equals(actingAdmin.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a different admin to transfer to");
        }

        Ticket ticket = getOnsiteTicketOrThrow(ticketId);
        if (ticket.getStatus() != TicketStatus.OPEN && ticket.getStatus() != TicketStatus.IN_PROGRESS) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Only waiting or in-progress tickets can be transferred"
            );
        }

        if (transferRequestRepository.existsPendingForTicket(ticketId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A transfer request is already pending for this ticket"
            );
        }

        QueueTransferRequest request = new QueueTransferRequest();
        request.setTicketId(ticketId);
        request.setFromAdminId(actingAdmin.getId());
        request.setToAdminId(target.getId());
        request.setStatus(QueueTransferStatus.PENDING);
        request.setCreatedAt(Instant.now());
        QueueTransferRequest saved = transferRequestRepository.persist(request);

        log.info(
                "Queue transfer requested ticket={} from={} to={}",
                ticket.getTicketNumber(),
                actingAdmin.getEmail(),
                target.getEmail()
        );
        return toTransferResponse(saved, ticket, actingAdmin.getName(), target.getName());
    }

    /** Target admin accepts the transfer — ticket is claimed or reassigned. */
    @Transactional
    public Ticket approveTransfer(AuthenticatedUser actingAdmin, Long transferId) {
        QueueTransferRequest request = getPendingTransferOrThrow(transferId);
        if (!request.getToAdminId().equals(actingAdmin.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the receiving admin can approve this transfer");
        }

        User target = requireActiveAdmin(actingAdmin.getId(), "Your account must be active to accept a transfer");
        requireNotAlreadyServing(target.getId());

        Ticket ticket = getOnsiteTicketOrThrow(request.getTicketId());
        if (ticket.getStatus() != TicketStatus.OPEN && ticket.getStatus() != TicketStatus.IN_PROGRESS) {
            cancelTransfer(request, "Ticket is no longer transferable");
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That ticket can no longer be transferred");
        }

        Ticket saved;
        if (ticket.getStatus() == TicketStatus.OPEN) {
            var lockedOpt = ticketRepository.lockWaitingOnsiteById(ticket.getId());
            if (lockedOpt.isEmpty()) {
                cancelTransfer(request, "Ticket is no longer waiting");
                throw new ResponseStatusException(HttpStatus.CONFLICT, "That ticket is no longer waiting");
            }
            saved = beginServing(lockedOpt.get(), target.getId());
        } else {
            ticket.setAssignedAdminId(target.getId());
            ticket.setUpdatedAt(Instant.now());
            saved = ticketRepository.save(ticket);
        }

        request.setStatus(QueueTransferStatus.APPROVED);
        request.setResolvedAt(Instant.now());
        transferRequestRepository.save(request);

        log.info(
                "Queue transfer approved id={} ticket={} toAdmin={}",
                transferId,
                saved.getTicketNumber(),
                actingAdmin.getEmail()
        );
        return saved;
    }

    /** Target admin declines the transfer. */
    @Transactional
    public QueueTransferResponse rejectTransfer(AuthenticatedUser actingAdmin, Long transferId) {
        QueueTransferRequest request = getPendingTransferOrThrow(transferId);
        if (!request.getToAdminId().equals(actingAdmin.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the receiving admin can reject this transfer");
        }

        request.setStatus(QueueTransferStatus.REJECTED);
        request.setResolvedAt(Instant.now());
        QueueTransferRequest saved = transferRequestRepository.save(request);

        Ticket ticket = ticketRepository.findById(saved.getTicketId()).orElse(null);
        Map<Long, User> admins = loadTransferAdmins(List.of(saved));
        log.info("Queue transfer rejected id={} by={}", transferId, actingAdmin.getEmail());
        return toTransferResponse(
                saved,
                ticket,
                nameOf(admins.get(saved.getFromAdminId())),
                nameOf(admins.get(saved.getToAdminId()))
        );
    }

    /** Requester cancels their own pending transfer. */
    @Transactional
    public QueueTransferResponse cancelTransferRequest(AuthenticatedUser actingAdmin, Long transferId) {
        QueueTransferRequest request = getPendingTransferOrThrow(transferId);
        if (!request.getFromAdminId().equals(actingAdmin.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the requester can cancel this transfer");
        }

        request.setStatus(QueueTransferStatus.CANCELLED);
        request.setResolvedAt(Instant.now());
        QueueTransferRequest saved = transferRequestRepository.save(request);

        Ticket ticket = ticketRepository.findById(saved.getTicketId()).orElse(null);
        Map<Long, User> admins = loadTransferAdmins(List.of(saved));
        log.info("Queue transfer cancelled id={} by={}", transferId, actingAdmin.getEmail());
        return toTransferResponse(
                saved,
                ticket,
                nameOf(admins.get(saved.getFromAdminId())),
                nameOf(admins.get(saved.getToAdminId()))
        );
    }

    @Transactional
    public Ticket completeServing(Long ticketId) {
        Ticket ticket = getOnsiteTicketOrThrow(ticketId);
        ticket.setStatus(TicketStatus.CLOSED);
        ticket.setResolvedAt(Instant.now());
        ticket.setUpdatedAt(Instant.now());
        Ticket saved = ticketRepository.save(ticket);
        cancelPendingTransfersForTicket(ticketId);
        notifyStatus(saved, TicketStatus.CLOSED);
        log.info("Queue ticket {} completed and closed", saved.getTicketNumber());
        return saved;
    }

    @Transactional
    public Ticket requeue(Long ticketId) {
        Ticket ticket = getOnsiteTicketOrThrow(ticketId);
        ticket.setStatus(TicketStatus.OPEN);
        ticket.setAssignedAdminId(null);
        ticket.setUpdatedAt(Instant.now());
        Ticket saved = ticketRepository.save(ticket);
        cancelPendingTransfersForTicket(ticketId);
        log.info("Queue ticket {} sent back to waiting", saved.getTicketNumber());
        return saved;
    }

    @Transactional(readOnly = true)
    public QueueSnapshotResponse getSnapshot() {
        List<Ticket> waiting = ticketRepository.findWaitingOnsiteOrderByQueueNumber();
        List<Ticket> serving = ticketRepository.findServingOnsite();
        List<QueueTransferRequest> pending = transferRequestRepository.findPending();

        Set<Long> adminIds = new HashSet<>();
        serving.stream().map(Ticket::getAssignedAdminId).forEach(adminIds::add);
        pending.forEach(r -> {
            adminIds.add(r.getFromAdminId());
            adminIds.add(r.getToAdminId());
        });

        Map<Long, User> adminsById = userRepository.findByIdIn(adminIds.stream().toList());

        List<QueueSnapshotResponse.NowServingEntry> nowServing = serving.stream()
                .map(t -> {
                    User admin = adminsById.get(t.getAssignedAdminId());
                    return new QueueSnapshotResponse.NowServingEntry(
                            t.getAssignedAdminId(),
                            admin != null ? admin.getName() : "Unknown",
                            TicketResponse.from(t, admin != null ? admin.getName() : null)
                    );
                })
                .toList();

        Map<Long, Ticket> ticketsById = Stream.concat(waiting.stream(), serving.stream())
                .collect(Collectors.toMap(Ticket::getId, t -> t, (a, b) -> a));
        // Load any transfer tickets not in waiting/serving (edge case)
        List<Long> missingTicketIds = pending.stream()
                .map(QueueTransferRequest::getTicketId)
                .filter(id -> !ticketsById.containsKey(id))
                .distinct()
                .toList();
        for (Long id : missingTicketIds) {
            ticketRepository.findById(id).ifPresent(t -> ticketsById.put(t.getId(), t));
        }

        List<QueueTransferResponse> pendingTransfers = pending.stream()
                .map(r -> toTransferResponse(
                        r,
                        ticketsById.get(r.getTicketId()),
                        nameOf(adminsById.get(r.getFromAdminId())),
                        nameOf(adminsById.get(r.getToAdminId()))
                ))
                .toList();

        List<TicketResponse> waitingResponses = waiting.stream().map(TicketResponse::from).toList();
        return new QueueSnapshotResponse(waitingResponses, nowServing, pendingTransfers);
    }

    private User requireActiveAdmin(Long adminId, String missingMessage) {
        if (adminId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, missingMessage);
        }
        User user = userRepository.findById(adminId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Admin not found"));
        if (!ADMIN_ROLES.contains(user.getRole())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Target user is not an admin");
        }
        if (!user.isActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Target admin account is not active");
        }
        return user;
    }

    private QueueTransferRequest getPendingTransferOrThrow(Long transferId) {
        QueueTransferRequest request = transferRequestRepository.findById(transferId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transfer request not found"));
        if (request.getStatus() != QueueTransferStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This transfer request is no longer pending");
        }
        return request;
    }

    private void cancelPendingTransfersForTicket(Long ticketId) {
        transferRequestRepository.findPending().stream()
                .filter(r -> r.getTicketId().equals(ticketId))
                .forEach(r -> {
                    r.setStatus(QueueTransferStatus.CANCELLED);
                    r.setResolvedAt(Instant.now());
                    transferRequestRepository.save(r);
                });
    }

    private void cancelTransfer(QueueTransferRequest request, String reason) {
        request.setStatus(QueueTransferStatus.CANCELLED);
        request.setResolvedAt(Instant.now());
        transferRequestRepository.save(request);
        log.info("Queue transfer {} cancelled: {}", request.getId(), reason);
    }

    private Map<Long, User> loadTransferAdmins(List<QueueTransferRequest> requests) {
        Set<Long> ids = new HashSet<>();
        requests.forEach(r -> {
            ids.add(r.getFromAdminId());
            ids.add(r.getToAdminId());
        });
        return userRepository.findByIdIn(ids.stream().toList());
    }

    private QueueTransferResponse toTransferResponse(
            QueueTransferRequest request,
            Ticket ticket,
            String fromName,
            String toName
    ) {
        return new QueueTransferResponse(
                request.getId(),
                request.getTicketId(),
                ticket != null ? ticket.getTicketNumber() : null,
                ticket != null ? ticket.getQueueNumber() : null,
                ticket != null ? ticket.getRequesterName() : null,
                ticket != null ? ticket.getRequesterPersonNo() : null,
                ticket != null ? ticketCategoryService.labelOf(ticket.getCategory()) : null,
                request.getFromAdminId(),
                fromName,
                request.getToAdminId(),
                toName,
                request.getStatus().name(),
                request.getCreatedAt()
        );
    }

    private static String nameOf(User user) {
        return user != null ? user.getName() : "Unknown";
    }

    private Ticket beginServing(Ticket ticket, Long adminId) {
        ticket.setAssignedAdminId(adminId);
        ticket.setStatus(TicketStatus.IN_PROGRESS);
        ticket.setUpdatedAt(Instant.now());
        Ticket saved = ticketRepository.save(ticket);
        notifyStatus(saved, TicketStatus.IN_PROGRESS);
        log.info("Queue ticket {} assigned to adminId={}", saved.getTicketNumber(), adminId);
        return saved;
    }

    private void notifyStatus(Ticket ticket, TicketStatus status) {
        if (ticket.getEmailThreadRootId() == null || ticket.getEmailThreadRootId().isBlank()) {
            String rootId = ticketThreadEmailService.newMessageId(ticket.getId(), "root");
            ticket.setEmailThreadRootId(rootId);
            ticket.setUpdatedAt(Instant.now());
            ticketRepository.save(ticket);
        }
        String messageId = ticketThreadEmailService.newMessageId(ticket.getId(), "status-" + status.name().toLowerCase());
        ticketThreadEmailService.sendStatusChangeAsync(
                ticket,
                status,
                ticket.getEmailThreadRootId(),
                messageId
        );
    }

    private void requireNotAlreadyServing(Long adminId) {
        boolean alreadyServing = ticketRepository.findServingOnsite().stream()
                .anyMatch(t -> adminId.equals(t.getAssignedAdminId()));
        if (alreadyServing) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "That counter is already serving someone — complete or requeue first"
            );
        }
    }

    private static String resolveRequesterEmail(String rawEmail, String personType, String personNo) {
        String email = rawEmail != null ? rawEmail.trim().toLowerCase() : "";
        if (!email.isBlank() && !PendingRequesterEmail.isPending(email)) {
            return email;
        }
        if (personType != null && personNo != null) {
            return PendingRequesterEmail.forPerson(personType, personNo);
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is required");
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private Ticket getOnsiteTicketOrThrow(Long ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found"));
        if (ticket.getChannel() != TicketChannel.ONSITE_RFID) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Not a queue ticket");
        }
        return ticket;
    }

}

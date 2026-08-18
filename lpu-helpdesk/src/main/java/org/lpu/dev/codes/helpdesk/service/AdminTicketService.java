package org.lpu.dev.codes.helpdesk.service;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lpu.dev.codes.helpdesk.model.Role;
import org.lpu.dev.codes.helpdesk.model.Ticket;
import org.lpu.dev.codes.helpdesk.model.TicketStatus;
import org.lpu.dev.codes.helpdesk.model.User;
import org.lpu.dev.codes.helpdesk.repository.TicketRepository;
import org.lpu.dev.codes.helpdesk.repository.UserRepository;
import org.lpu.dev.codes.helpdesk.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Ticket assignment and status changes for the admin portal. Visibility is a
 * shared pool across online and onsite tickets: any admin can see, assign, and
 * change status regardless of who a ticket is assigned to.
 */
@Service
public class AdminTicketService {

    private static final Logger log = LogManager.getLogger(AdminTicketService.class);
    private static final Set<Role> ADMIN_ROLES = EnumSet.of(Role.ADMIN, Role.SUPER_ADMIN);

    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final TicketThreadEmailService ticketThreadEmailService;

    public AdminTicketService(
            TicketRepository ticketRepository,
            UserRepository userRepository,
            TicketThreadEmailService ticketThreadEmailService
    ) {
        this.ticketRepository = ticketRepository;
        this.userRepository = userRepository;
        this.ticketThreadEmailService = ticketThreadEmailService;
    }

    @Transactional(readOnly = true)
    public List<Ticket> listTickets(TicketStatus status) {
        return ticketRepository.findAllOrderByCreatedAtDesc(status);
    }

    @Transactional(readOnly = true)
    public List<Ticket> listHistoryForPerson(String email, String personType, String personNo) {
        boolean hasEmail = email != null && !email.isBlank();
        boolean hasPerson = personType != null && !personType.isBlank()
                && personNo != null && !personNo.isBlank();
        if (!hasEmail && !hasPerson) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Provide email or personType with personNo"
            );
        }
        return ticketRepository.findHistoryForPerson(email, personType, personNo);
    }

    @Transactional
    public Ticket assignTicket(AuthenticatedUser actingAdmin, Long ticketId, Long targetAdminId) {
        Ticket ticket = getTicketOrThrow(ticketId);

        if (targetAdminId != null) {
            User target = userRepository.findById(targetAdminId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Admin not found"));
            if (!ADMIN_ROLES.contains(target.getRole())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Target user is not an admin");
            }
        }

        ticket.setAssignedAdminId(targetAdminId);
        ticket.setUpdatedAt(Instant.now());
        Ticket saved = ticketRepository.save(ticket);
        log.info("Ticket {} assigned to adminId={} by={}", saved.getTicketNumber(), targetAdminId, actingAdmin.getEmail());
        return saved;
    }

    @Transactional
    public Ticket updateStatus(AuthenticatedUser actingAdmin, Long ticketId, String rawStatus) {
        TicketStatus newStatus = parseStatus(rawStatus);
        Ticket ticket = getTicketOrThrow(ticketId);
        TicketStatus previousStatus = ticket.getStatus();

        // Leaving OPEN requires an assignee: claim the ticket for the acting admin if still unassigned.
        if (previousStatus == TicketStatus.OPEN
                && newStatus != TicketStatus.OPEN
                && ticket.getAssignedAdminId() == null) {
            ticket.setAssignedAdminId(actingAdmin.getId());
            log.info(
                    "Ticket {} auto-assigned to adminId={} on status change from OPEN to {}",
                    ticket.getTicketNumber(),
                    actingAdmin.getId(),
                    newStatus
            );
        }

        ticket.setStatus(newStatus);
        ticket.setUpdatedAt(Instant.now());
        if (newStatus == TicketStatus.RESOLVED || newStatus == TicketStatus.CLOSED) {
            ticket.setResolvedAt(Instant.now());
        } else {
            ticket.setResolvedAt(null);
        }

        Ticket saved = ticketRepository.save(ticket);
        log.info("Ticket {} status set to {} by={}", saved.getTicketNumber(), newStatus, actingAdmin.getEmail());

        if (previousStatus != newStatus
                && (newStatus == TicketStatus.IN_PROGRESS
                || newStatus == TicketStatus.RESOLVED
                || newStatus == TicketStatus.CLOSED)) {
            ensureThreadRoot(saved);
            String messageId = ticketThreadEmailService.newMessageId(saved.getId(), "status-" + newStatus.name().toLowerCase());
            ticketThreadEmailService.sendStatusChangeAsync(
                    saved,
                    newStatus,
                    saved.getEmailThreadRootId(),
                    messageId
            );
        }

        return saved;
    }

    private void ensureThreadRoot(Ticket ticket) {
        if (ticket.getEmailThreadRootId() != null && !ticket.getEmailThreadRootId().isBlank()) {
            return;
        }
        String rootId = ticketThreadEmailService.newMessageId(ticket.getId(), "root");
        ticket.setEmailThreadRootId(rootId);
        ticket.setUpdatedAt(Instant.now());
        ticketRepository.save(ticket);
    }

    private Ticket getTicketOrThrow(Long ticketId) {
        return ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found"));
    }

    private TicketStatus parseStatus(String rawStatus) {
        try {
            return TicketStatus.valueOf(rawStatus.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown ticket status: " + rawStatus);
        }
    }
}

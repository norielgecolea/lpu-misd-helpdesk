package org.lpu.dev.codes.helpdesk.service;

import java.time.Instant;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lpu.dev.codes.helpdesk.dto.PendingCsmResponse;
import org.lpu.dev.codes.helpdesk.model.CsmRating;
import org.lpu.dev.codes.helpdesk.model.Ticket;
import org.lpu.dev.codes.helpdesk.model.TicketCsm;
import org.lpu.dev.codes.helpdesk.model.TicketStatus;
import org.lpu.dev.codes.helpdesk.repository.TicketCsmRepository;
import org.lpu.dev.codes.helpdesk.repository.TicketRepository;
import org.lpu.dev.codes.helpdesk.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TicketCsmService {

    private static final Logger log = LogManager.getLogger(TicketCsmService.class);

    private final TicketRepository ticketRepository;
    private final TicketCsmRepository ticketCsmRepository;

    public TicketCsmService(TicketRepository ticketRepository, TicketCsmRepository ticketCsmRepository) {
        this.ticketRepository = ticketRepository;
        this.ticketCsmRepository = ticketCsmRepository;
    }

    @Transactional(readOnly = true)
    public Optional<PendingCsmResponse> pendingForUser(AuthenticatedUser user) {
        return ticketRepository
                .findOldestUnratedClosedByUserIdOrEmail(user.getId(), user.getEmail())
                .map(PendingCsmResponse::from);
    }

    @Transactional(readOnly = true)
    public Optional<PendingCsmResponse> pendingForPerson(String email, String personType, String personNo) {
        return ticketRepository
                .findOldestUnratedClosedForPerson(email, personType, personNo)
                .map(PendingCsmResponse::from);
    }

    @Transactional(readOnly = true)
    public void requireNoPendingForUser(AuthenticatedUser user) {
        if (ticketRepository.hasUnratedClosedByUserIdOrEmail(user.getId(), user.getEmail())) {
            throw pendingConflict();
        }
    }

    @Transactional(readOnly = true)
    public void requireNoPendingForPerson(String email, String personType, String personNo) {
        if (ticketRepository.hasUnratedClosedForPerson(email, personType, personNo)) {
            throw pendingConflict();
        }
    }

    @Transactional
    public PendingCsmResponse submitForUser(AuthenticatedUser user, Long ticketId, String rawRating, String comment) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found"));
        if (!ownsTicket(user, ticket)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot rate this ticket");
        }
        return submit(ticket, rawRating, comment);
    }

    @Transactional
    public PendingCsmResponse submitForPerson(
            Long ticketId,
            String email,
            String personType,
            String personNo,
            String rawRating,
            String comment
    ) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found"));
        if (!belongsToPerson(ticket, email, personType, personNo)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot rate this ticket");
        }
        return submit(ticket, rawRating, comment);
    }

    private PendingCsmResponse submit(Ticket ticket, String rawRating, String comment) {
        if (ticket.getStatus() != TicketStatus.CLOSED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only closed tickets can be rated");
        }
        if (ticketCsmRepository.existsByTicketId(ticket.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This ticket was already rated");
        }

        CsmRating rating = parseRating(rawRating);
        String trimmedComment = comment != null ? comment.trim() : "";
        if (rating != CsmRating.SAD) {
            trimmedComment = "";
        } else if (trimmedComment.length() > 2000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Comment is too long");
        }

        TicketCsm csm = new TicketCsm();
        csm.setTicketId(ticket.getId());
        csm.setRating(rating);
        csm.setComment(trimmedComment.isBlank() ? null : trimmedComment);
        csm.setChannel(ticket.getChannel());
        csm.setSubmittedAt(Instant.now());
        ticketCsmRepository.persist(csm);

        log.info(
                "CSM submitted ticketNumber={} rating={} channel={}",
                ticket.getTicketNumber(),
                rating,
                ticket.getChannel()
        );
        return PendingCsmResponse.from(ticket);
    }

    private static boolean ownsTicket(AuthenticatedUser user, Ticket ticket) {
        boolean byUserId = user.getId() != null && user.getId().equals(ticket.getRequesterUserId());
        boolean byEmail = user.getEmail() != null
                && user.getEmail().equalsIgnoreCase(ticket.getRequesterEmail());
        return byUserId || byEmail;
    }

    private static boolean belongsToPerson(Ticket ticket, String email, String personType, String personNo) {
        if (email != null && !email.isBlank()
                && ticket.getRequesterEmail() != null
                && email.equalsIgnoreCase(ticket.getRequesterEmail())) {
            return true;
        }
        if (personType != null && personNo != null
                && personType.equalsIgnoreCase(nullToEmpty(ticket.getRequesterPersonType()))
                && personNo.equalsIgnoreCase(nullToEmpty(ticket.getRequesterPersonNo()))) {
            return true;
        }
        return false;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static CsmRating parseRating(String raw) {
        try {
            return CsmRating.valueOf(raw.trim().toUpperCase());
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid rating. Use SAD, NEUTRAL, or HAPPY.");
        }
    }

    private static ResponseStatusException pendingConflict() {
        return new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Please rate your closed ticket before creating a new one."
        );
    }
}

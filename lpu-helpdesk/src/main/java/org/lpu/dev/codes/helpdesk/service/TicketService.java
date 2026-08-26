package org.lpu.dev.codes.helpdesk.service;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.lpu.dev.codes.helpdesk.config.AuthProperties;
import org.lpu.dev.codes.helpdesk.dto.DirectoryProfileResponse;
import org.lpu.dev.codes.helpdesk.dto.TicketCreateRequest;
import org.lpu.dev.codes.helpdesk.model.Role;
import org.lpu.dev.codes.helpdesk.model.Ticket;
import org.lpu.dev.codes.helpdesk.model.TicketChannel;
import org.lpu.dev.codes.helpdesk.model.TicketStatus;
import org.lpu.dev.codes.helpdesk.model.User;
import org.lpu.dev.codes.helpdesk.repository.TicketRepository;
import org.lpu.dev.codes.helpdesk.repository.UserRepository;
import org.lpu.dev.codes.helpdesk.security.AuthenticatedUser;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TicketService {

    private static final Set<TicketStatus> REQUESTER_CLOSEABLE = Set.of(
            TicketStatus.OPEN,
            TicketStatus.IN_PROGRESS,
            TicketStatus.RESOLVED
    );

    /** Student may reopen a closed ticket at most this many times; then it stays closed. */
    public static final int MAX_REQUESTER_REOPENS = 2;

    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final IdPhotoStorageService idPhotoStorageService;
    private final DirectoryLookupService directoryLookupService;
    private final TicketCategoryService ticketCategoryService;
    private final TicketCsmService ticketCsmService;
    private final TicketThreadEmailService ticketThreadEmailService;
    private final TicketConversationService ticketConversationService;
    private final AuthProperties authProperties;

    public TicketService(
            TicketRepository ticketRepository,
            UserRepository userRepository,
            IdPhotoStorageService idPhotoStorageService,
            DirectoryLookupService directoryLookupService,
            TicketCategoryService ticketCategoryService,
            TicketCsmService ticketCsmService,
            TicketThreadEmailService ticketThreadEmailService,
            TicketConversationService ticketConversationService,
            AuthProperties authProperties
    ) {
        this.ticketRepository = ticketRepository;
        this.userRepository = userRepository;
        this.idPhotoStorageService = idPhotoStorageService;
        this.directoryLookupService = directoryLookupService;
        this.ticketCategoryService = ticketCategoryService;
        this.ticketCsmService = ticketCsmService;
        this.ticketThreadEmailService = ticketThreadEmailService;
        this.ticketConversationService = ticketConversationService;
        this.authProperties = authProperties;
    }

    @Transactional
    public Ticket createOnlineTicket(
            AuthenticatedUser requester,
            TicketCreateRequest request,
            MultipartFile idPhoto,
            MultipartFile[] attachments
    ) {
        if (idPhoto == null || idPhoto.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please upload a picture of your ID");
        }

        ticketCsmService.requireNoPendingForUser(requester);

        TicketCategoryService.CategorySelection selection =
                ticketCategoryService.requireLeafForOnline(request.category(), request.subcategory());

        String requesterName;
        String personType = null;
        String personNo = null;
        String linkedLpuEmail = null;

        if (authProperties.isAllowedEmail(requester.getEmail())) {
            DirectoryProfileResponse profile = directoryLookupService.resolveProfile(requester.getEmail(), null, null);
            requesterName = profile.found() && profile.name() != null && !profile.name().isBlank()
                    ? profile.name().trim()
                    : (requester.getName() != null && !requester.getName().isBlank()
                            ? requester.getName()
                            : requester.getEmail());
            if (profile.found()) {
                personType = profile.personType();
                personNo = profile.personNo();
            }
        } else {
            User user = userRepository.findById(requester.getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
            String declaredName = user.getDeclaredStudentName();
            String declaredNo = user.getDeclaredStudentNo();
            String declaredType = user.getDeclaredPersonType();
            String declaredLpuEmail = user.getDeclaredLpuEmail();
            if (declaredName == null || declaredName.isBlank()
                    || declaredNo == null || declaredNo.isBlank()
                    || declaredType == null || declaredType.isBlank()
                    || declaredLpuEmail == null || declaredLpuEmail.isBlank()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Please enter your name, ID number, person type, and LPU email before creating a ticket"
                );
            }
            String type = declaredType.trim().toUpperCase();
            DirectoryProfileResponse profile = directoryLookupService.resolveIfIdAndLpuEmailMatch(
                    type,
                    declaredNo.trim(),
                    declaredLpuEmail.trim()
            );
            if (profile.found()) {
                requesterName = profile.name() != null && !profile.name().isBlank()
                        ? profile.name().trim()
                        : declaredName.trim();
                personType = profile.personType() != null ? profile.personType() : type;
                personNo = profile.personNo() != null ? profile.personNo() : declaredNo.trim();
            } else {
                // No verified directory match — keep exactly what the user entered.
                requesterName = declaredName.trim();
                personType = type;
                personNo = declaredNo.trim();
            }
            linkedLpuEmail = declaredLpuEmail.trim().toLowerCase();
        }

        String description = request.description().trim();

        Ticket ticket = new Ticket();
        ticket.setRequesterUserId(requester.getId());
        // Always the login address (outside email stays sender; never declared LPU email).
        ticket.setRequesterEmail(requester.getEmail());
        ticket.setRequesterName(requesterName);
        if (linkedLpuEmail != null) {
            ticket.setRequesterLpuEmail(linkedLpuEmail);
        }
        if (personType != null && personNo != null) {
            ticket.setRequesterPersonType(personType);
            ticket.setRequesterPersonNo(personNo);
        }
        ticket.setCategory(selection.parent().getCode());
        ticket.setSubcategory(selection.child().getCode());
        ticket.setSubject(request.subject().trim());
        ticket.setDescription(description);
        ticket.setStatus(TicketStatus.OPEN);
        ticket.setChannel(TicketChannel.ONLINE);

        ticketRepository.persist(ticket);
        ticket.setTicketNumber(Ticket.formatPublicNumber(ticket.getChannel(), ticket.getCreatedAt(), ticket.getId()));

        String filename = idPhotoStorageService.storeTicketIdPhoto(ticket.getId(), idPhoto);
        ticket.setIdPhotoFilename(filename);

        String rootMessageId = ticketThreadEmailService.newMessageId(ticket.getId(), "created");
        ticket.setEmailThreadRootId(rootMessageId);
        Ticket saved = ticketRepository.save(ticket);
        ticketThreadEmailService.sendTicketCreatedAsync(saved, rootMessageId);

        List<MultipartFile> attachmentList = attachments == null
                ? List.of()
                : Arrays.stream(attachments).filter(f -> f != null && !f.isEmpty()).toList();
        ticketConversationService.seedOpeningMessages(requester, saved, description, attachmentList);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Ticket> listMyTickets(AuthenticatedUser requester) {
        return ticketRepository.findMineByUserIdOrEmailOrderByCreatedAtDesc(
                requester.getId(),
                requester.getEmail()
        );
    }

    /**
     * Requester-only status changes: close an active ticket, or reopen a closed one.
     */
    @Transactional
    public Ticket updateMyTicketStatus(AuthenticatedUser requester, Long ticketId, String rawStatus) {
        TicketStatus newStatus;
        try {
            newStatus = TicketStatus.valueOf(rawStatus.trim().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status");
        }
        if (newStatus != TicketStatus.CLOSED && newStatus != TicketStatus.OPEN) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "You can only close or reopen tickets"
            );
        }

        Ticket ticket = requireOwnedTicket(requester, ticketId);
        TicketStatus previous = ticket.getStatus();

        if (newStatus == TicketStatus.CLOSED) {
            if (!REQUESTER_CLOSEABLE.contains(previous)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This ticket is already closed");
            }
            ticket.setStatus(TicketStatus.CLOSED);
            ticket.setResolvedAt(Instant.now());
        } else {
            if (previous != TicketStatus.CLOSED) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only closed tickets can be reopened");
            }
            if (ticket.getRequesterReopenCount() >= MAX_REQUESTER_REOPENS) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "This ticket was reopened the maximum of "
                                + MAX_REQUESTER_REOPENS
                                + " times and is now permanently closed"
                );
            }
            ticket.setRequesterReopenCount(ticket.getRequesterReopenCount() + 1);
            ticket.setStatus(TicketStatus.OPEN);
            ticket.setResolvedAt(null);
        }
        ticket.setUpdatedAt(Instant.now());
        Ticket saved = ticketRepository.save(ticket);

        if (previous != saved.getStatus() && saved.getStatus() == TicketStatus.CLOSED) {
            ensureThreadRoot(saved);
            String messageId = ticketThreadEmailService.newMessageId(
                    saved.getId(),
                    "status-" + saved.getStatus().name().toLowerCase()
            );
            ticketThreadEmailService.sendStatusChangeAsync(
                    saved,
                    saved.getStatus(),
                    saved.getEmailThreadRootId(),
                    messageId
            );
        }
        return saved;
    }

    @Transactional(readOnly = true)
    public Resource loadIdPhoto(AuthenticatedUser user, Long ticketId) {
        Ticket ticket = requireAccessibleTicket(user, ticketId);
        return idPhotoStorageService.load(ticket.getIdPhotoFilename());
    }

    @Transactional(readOnly = true)
    public MediaType idPhotoMediaType(AuthenticatedUser user, Long ticketId) {
        Ticket ticket = requireAccessibleTicket(user, ticketId);
        return idPhotoStorageService.mediaTypeFor(ticket.getIdPhotoFilename());
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

    private Ticket requireOwnedTicket(AuthenticatedUser user, Long ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found"));
        if (!ticket.matchesRequester(user.getId(), user.getEmail())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot update this ticket");
        }
        return ticket;
    }

    private Ticket requireAccessibleTicket(AuthenticatedUser user, Long ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found"));

        boolean isStaff = user.getRole() == Role.ADMIN || user.getRole() == Role.SUPER_ADMIN;
        if (!isStaff && !ticket.matchesRequester(user.getId(), user.getEmail())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot view this ticket's ID photo");
        }
        if (ticket.getIdPhotoFilename() == null || ticket.getIdPhotoFilename().isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No ID photo on file for this ticket");
        }
        return ticket;
    }

}

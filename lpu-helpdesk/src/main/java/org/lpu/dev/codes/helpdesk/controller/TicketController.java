package org.lpu.dev.codes.helpdesk.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.lpu.dev.codes.helpdesk.dto.PendingCsmResponse;
import org.lpu.dev.codes.helpdesk.dto.SubmitCsmRequest;
import org.lpu.dev.codes.helpdesk.dto.TicketCategoryOption;
import org.lpu.dev.codes.helpdesk.dto.TicketCreateRequest;
import org.lpu.dev.codes.helpdesk.dto.TicketMessageResponse;
import org.lpu.dev.codes.helpdesk.dto.TicketResponse;
import org.lpu.dev.codes.helpdesk.model.Ticket;
import org.lpu.dev.codes.helpdesk.model.User;
import org.lpu.dev.codes.helpdesk.repository.UserRepository;
import org.lpu.dev.codes.helpdesk.security.AuthenticatedUser;
import org.lpu.dev.codes.helpdesk.service.TicketCategoryService;
import org.lpu.dev.codes.helpdesk.service.TicketConversationService;
import org.lpu.dev.codes.helpdesk.service.TicketCsmService;
import org.lpu.dev.codes.helpdesk.service.TicketService;
import org.lpu.dev.codes.helpdesk.service.TicketUnreadService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private final TicketService ticketService;
    private final TicketCategoryService ticketCategoryService;
    private final TicketConversationService conversationService;
    private final TicketCsmService ticketCsmService;
    private final UserRepository userRepository;
    private final TicketUnreadService ticketUnreadService;

    public TicketController(
            TicketService ticketService,
            TicketCategoryService ticketCategoryService,
            TicketConversationService conversationService,
            TicketCsmService ticketCsmService,
            UserRepository userRepository,
            TicketUnreadService ticketUnreadService
    ) {
        this.ticketService = ticketService;
        this.ticketCategoryService = ticketCategoryService;
        this.conversationService = conversationService;
        this.ticketCsmService = ticketCsmService;
        this.userRepository = userRepository;
        this.ticketUnreadService = ticketUnreadService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<TicketResponse> create(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestPart("category") @NotBlank String category,
            @RequestPart("subject") @NotBlank @Size(max = 200) String subject,
            @RequestPart("description") @NotBlank @Size(max = 5000) String description,
            @RequestPart("idPhoto") MultipartFile idPhoto,
            @RequestPart(value = "attachments", required = false) MultipartFile[] attachments
    ) {
        TicketCreateRequest request = new TicketCreateRequest(category, subject, description);
        Ticket ticket = ticketService.createOnlineTicket(user, request, idPhoto, attachments);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(user, ticket));
    }

    @GetMapping("/mine")
    public ResponseEntity<List<TicketResponse>> mine(@AuthenticationPrincipal AuthenticatedUser user) {
        List<Ticket> tickets = ticketService.listMyTickets(user);
        return ResponseEntity.ok(toResponses(user, tickets));
    }

    @GetMapping("/pending-csm")
    public ResponseEntity<PendingCsmResponse> pendingCsm(@AuthenticationPrincipal AuthenticatedUser user) {
        return ticketCsmService.pendingForUser(user)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/{id}/csm")
    public ResponseEntity<PendingCsmResponse> submitCsm(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id,
            @Valid @RequestBody SubmitCsmRequest request
    ) {
        return ResponseEntity.ok(ticketCsmService.submitForUser(user, id, request.rating(), request.comment()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TicketResponse> getOne(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id
    ) {
        Ticket ticket = conversationService.getAccessibleTicket(user, id);
        return ResponseEntity.ok(toResponse(user, ticket));
    }

    @GetMapping("/{id}/messages")
    public ResponseEntity<List<TicketMessageResponse>> messages(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(conversationService.listMessages(user, id));
    }

    @PostMapping(value = "/{id}/messages", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<TicketMessageResponse> postMessage(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id,
            @RequestParam(value = "body", required = false) String body,
            @RequestPart(value = "attachment", required = false) MultipartFile attachment
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(conversationService.postMessage(user, id, body, attachment));
    }

    @GetMapping("/{id}/id-photo")
    public ResponseEntity<Resource> idPhoto(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id
    ) {
        Resource resource = ticketService.loadIdPhoto(user, id);
        MediaType mediaType = ticketService.idPhotoMediaType(user, id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .contentType(mediaType)
                .body(resource);
    }

    @GetMapping("/{id}/messages/{messageId}/attachment")
    public ResponseEntity<Resource> messageAttachment(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id,
            @PathVariable Long messageId
    ) {
        Resource resource = conversationService.loadAttachment(user, id, messageId);
        MediaType mediaType = conversationService.attachmentMediaType(user, id, messageId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .contentType(mediaType)
                .body(resource);
    }

    @GetMapping("/categories")
    public ResponseEntity<List<TicketCategoryOption>> categories() {
        return ResponseEntity.ok(ticketCategoryService.listForOnline());
    }

    private List<TicketResponse> toResponses(AuthenticatedUser user, List<Ticket> tickets) {
        List<Long> adminIds = tickets.stream()
                .map(Ticket::getAssignedAdminId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, User> admins = userRepository.findByIdIn(adminIds);
        List<Long> ticketIds = tickets.stream().map(Ticket::getId).toList();
        Map<Long, Integer> unread = ticketUnreadService.unreadCounts(user.getId(), ticketIds);
        return tickets.stream()
                .map(t -> {
                    User admin = t.getAssignedAdminId() != null ? admins.get(t.getAssignedAdminId()) : null;
                    return TicketResponse.from(
                            t,
                            admin != null ? admin.getName() : null,
                            unread.getOrDefault(t.getId(), 0)
                    );
                })
                .toList();
    }

    private TicketResponse toResponse(AuthenticatedUser user, Ticket ticket) {
        String adminName = null;
        if (ticket.getAssignedAdminId() != null) {
            adminName = userRepository.findById(ticket.getAssignedAdminId())
                    .map(User::getName)
                    .orElse(null);
        }
        int unread = ticketUnreadService.unreadCounts(user.getId(), List.of(ticket.getId()))
                .getOrDefault(ticket.getId(), 0);
        return TicketResponse.from(ticket, adminName, unread);
    }
}

package org.lpu.dev.codes.helpdesk.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.lpu.dev.codes.helpdesk.dto.TicketMessageResponse;
import org.lpu.dev.codes.helpdesk.model.Role;
import org.lpu.dev.codes.helpdesk.model.Ticket;
import org.lpu.dev.codes.helpdesk.model.TicketMessage;
import org.lpu.dev.codes.helpdesk.model.TicketStatus;
import org.lpu.dev.codes.helpdesk.repository.TicketMessageRepository;
import org.lpu.dev.codes.helpdesk.repository.TicketRepository;
import org.lpu.dev.codes.helpdesk.security.AuthenticatedUser;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TicketConversationService {

    private static final int MAX_CREATE_ATTACHMENTS = 5;

    private final TicketRepository ticketRepository;
    private final TicketMessageRepository ticketMessageRepository;
    private final TicketThreadEmailService ticketThreadEmailService;
    private final TicketUnreadService ticketUnreadService;
    private final IdPhotoStorageService idPhotoStorageService;

    public TicketConversationService(
            TicketRepository ticketRepository,
            TicketMessageRepository ticketMessageRepository,
            TicketThreadEmailService ticketThreadEmailService,
            TicketUnreadService ticketUnreadService,
            IdPhotoStorageService idPhotoStorageService
    ) {
        this.ticketRepository = ticketRepository;
        this.ticketMessageRepository = ticketMessageRepository;
        this.ticketThreadEmailService = ticketThreadEmailService;
        this.ticketUnreadService = ticketUnreadService;
        this.idPhotoStorageService = idPhotoStorageService;
    }

    @Transactional(readOnly = true)
    public Ticket getAccessibleTicket(AuthenticatedUser user, Long ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found"));
        requireAccess(user, ticket);
        return ticket;
    }

    @Transactional
    public List<TicketMessageResponse> listMessages(AuthenticatedUser user, Long ticketId) {
        getAccessibleTicket(user, ticketId);
        List<TicketMessage> messages = ticketMessageRepository.findByTicketIdOrderByCreatedAtAsc(ticketId);
        if (!messages.isEmpty()) {
            ticketUnreadService.markReadUpTo(
                    user.getId(),
                    ticketId,
                    messages.get(messages.size() - 1).getId()
            );
        }
        return messages.stream().map(TicketMessageResponse::from).toList();
    }

    /**
     * Seeds the conversation with the ticket description as the first message,
     * attaching the first image (if any) and posting remaining images as follow-ups.
     */
    @Transactional
    public void seedOpeningMessages(
            AuthenticatedUser author,
            Ticket ticket,
            String description,
            List<MultipartFile> attachments
    ) {
        List<MultipartFile> files = normalizeAttachments(attachments);
        MultipartFile first = files.isEmpty() ? null : files.get(0);
        persistMessage(author, ticket, description != null ? description.trim() : "", first, false);

        for (int i = 1; i < files.size(); i++) {
            persistMessage(author, ticket, "", files.get(i), false);
        }
    }

    @Transactional
    public TicketMessageResponse postMessage(
            AuthenticatedUser user,
            Long ticketId,
            String rawBody,
            MultipartFile attachment
    ) {
        Ticket ticket = getAccessibleTicket(user, ticketId);
        if (ticket.getStatus() == TicketStatus.CLOSED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This ticket is closed");
        }

        String body = rawBody != null ? rawBody.trim() : "";
        boolean hasAttachment = attachment != null && !attachment.isEmpty();
        if (body.isBlank() && !hasAttachment) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message or picture is required");
        }
        if (body.length() > 5000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message must be at most 5000 characters");
        }

        TicketMessage message = persistMessage(user, ticket, body, hasAttachment ? attachment : null, true);
        return TicketMessageResponse.from(message);
    }

    @Transactional(readOnly = true)
    public Resource loadAttachment(AuthenticatedUser user, Long ticketId, Long messageId) {
        TicketMessage message = requireMessageWithAttachment(user, ticketId, messageId);
        return idPhotoStorageService.loadMessageAttachment(message.getAttachmentFilename());
    }

    @Transactional(readOnly = true)
    public MediaType attachmentMediaType(AuthenticatedUser user, Long ticketId, Long messageId) {
        TicketMessage message = requireMessageWithAttachment(user, ticketId, messageId);
        return idPhotoStorageService.mediaTypeForContentType(
                message.getAttachmentContentType(),
                message.getAttachmentFilename()
        );
    }

    private TicketMessage requireMessageWithAttachment(AuthenticatedUser user, Long ticketId, Long messageId) {
        getAccessibleTicket(user, ticketId);
        TicketMessage message = ticketMessageRepository.findById(messageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found"));
        if (!ticketId.equals(message.getTicketId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found");
        }
        if (!message.hasAttachment()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Attachment not found");
        }
        return message;
    }

    private TicketMessage persistMessage(
            AuthenticatedUser user,
            Ticket ticket,
            String body,
            MultipartFile attachment,
            boolean notifyEmail
    ) {
        TicketMessage message = new TicketMessage();
        message.setTicketId(ticket.getId());
        message.setAuthorUserId(user.getId());
        message.setAuthorEmail(user.getEmail());
        message.setAuthorName(resolveAuthorName(user, ticket));
        message.setAuthorRole(user.getRole().name());
        message.setBody(body != null ? body : "");
        message.setCreatedAt(Instant.now());

        if (attachment != null && !attachment.isEmpty()) {
            String filename = idPhotoStorageService.storeMessageAttachment(ticket.getId(), attachment);
            message.setAttachmentFilename(filename);
            message.setAttachmentContentType(normalizeContentType(attachment.getContentType()));
            message.setAttachmentOriginalName(safeOriginalName(attachment.getOriginalFilename()));
        }

        ticketMessageRepository.persist(message);

        String domain = ticketThreadEmailService.mailDomain();
        String messageId = "<ticket-" + ticket.getId() + "-msg-" + message.getId() + "@" + domain + ">";
        String rootId = ticket.getEmailThreadRootId();
        boolean firstInThread = rootId == null || rootId.isBlank();
        if (firstInThread) {
            rootId = messageId;
            ticket.setEmailThreadRootId(rootId);
            ticket.setUpdatedAt(Instant.now());
            ticketRepository.save(ticket);
        }

        message.setEmailMessageId(messageId);
        ticketMessageRepository.save(message);

        if (notifyEmail && isStaff(user)) {
            String emailBody = message.getBody();
            if (emailBody == null || emailBody.isBlank()) {
                emailBody = message.hasAttachment() ? "(Image attached)" : "";
            }
            ticketThreadEmailService.sendAgentReplyAsync(
                    ticket.getTicketNumber(),
                    ticket.getRequesterEmail(),
                    firstInThread ? null : rootId,
                    messageId,
                    emailBody
            );
        }

        ticketUnreadService.markReadUpTo(user.getId(), ticket.getId(), message.getId());
        return message;
    }

    private static List<MultipartFile> normalizeAttachments(List<MultipartFile> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return List.of();
        }
        List<MultipartFile> files = new ArrayList<>();
        for (MultipartFile file : attachments) {
            if (file != null && !file.isEmpty()) {
                files.add(file);
            }
        }
        if (files.size() > MAX_CREATE_ATTACHMENTS) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "You can attach up to " + MAX_CREATE_ATTACHMENTS + " pictures"
            );
        }
        return files;
    }

    private static String normalizeContentType(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String type = raw.trim().toLowerCase();
        int semi = type.indexOf(';');
        return semi >= 0 ? type.substring(0, semi).trim() : type;
    }

    private static String safeOriginalName(String name) {
        if (name == null || name.isBlank()) {
            return "image";
        }
        String trimmed = name.trim();
        return trimmed.length() > 255 ? trimmed.substring(0, 255) : trimmed;
    }

    private void requireAccess(AuthenticatedUser user, Ticket ticket) {
        if (isStaff(user)) {
            return;
        }
        boolean ownsByUserId = user.getId().equals(ticket.getRequesterUserId());
        boolean ownsByEmail = user.getEmail() != null
                && user.getEmail().equalsIgnoreCase(ticket.getRequesterEmail());
        if (!ownsByUserId && !ownsByEmail) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot access this ticket");
        }
    }

    private static boolean isStaff(AuthenticatedUser user) {
        Role role = user.getRole();
        return role == Role.ADMIN || role == Role.SUPER_ADMIN;
    }

    private static String resolveAuthorName(AuthenticatedUser user, Ticket ticket) {
        if (user.getRole() == Role.USER) {
            String ticketName = ticket.getRequesterName();
            if (ticketName != null && !ticketName.isBlank()) {
                return ticketName.trim();
            }
        }
        if (user.getName() != null && !user.getName().isBlank()) {
            return user.getName().trim();
        }
        return user.getEmail();
    }
}

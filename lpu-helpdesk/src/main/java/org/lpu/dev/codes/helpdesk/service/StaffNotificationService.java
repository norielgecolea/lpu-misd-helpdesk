package org.lpu.dev.codes.helpdesk.service;

import java.time.Instant;
import java.util.List;
import org.lpu.dev.codes.helpdesk.dto.StaffNotificationListResponse;
import org.lpu.dev.codes.helpdesk.dto.StaffNotificationResponse;
import org.lpu.dev.codes.helpdesk.model.Role;
import org.lpu.dev.codes.helpdesk.model.StaffNotification;
import org.lpu.dev.codes.helpdesk.model.StaffNotificationType;
import org.lpu.dev.codes.helpdesk.model.Ticket;
import org.lpu.dev.codes.helpdesk.model.TicketChannel;
import org.lpu.dev.codes.helpdesk.model.User;
import org.lpu.dev.codes.helpdesk.repository.StaffNotificationRepository;
import org.lpu.dev.codes.helpdesk.repository.UserRepository;
import org.lpu.dev.codes.helpdesk.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class StaffNotificationService {

    private static final List<Role> ADMIN_ROLES = List.of(Role.ADMIN, Role.SUPER_ADMIN);
    private static final int BODY_MAX = 1000;

    private final StaffNotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public StaffNotificationService(
            StaffNotificationRepository notificationRepository,
            UserRepository userRepository
    ) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public StaffNotificationListResponse listFor(AuthenticatedUser admin) {
        List<StaffNotificationResponse> items = notificationRepository
                .findRecentForRecipient(admin.getId())
                .stream()
                .map(StaffNotificationResponse::from)
                .toList();
        long unread = notificationRepository.countUnread(admin.getId());
        return new StaffNotificationListResponse(items, unread);
    }

    @Transactional
    public StaffNotificationResponse markRead(AuthenticatedUser admin, Long id) {
        StaffNotification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
        if (!admin.getId().equals(notification.getRecipientId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found");
        }
        if (notification.getReadAt() == null) {
            notification.setReadAt(Instant.now());
            notificationRepository.save(notification);
        }
        return StaffNotificationResponse.from(notification);
    }

    @Transactional
    public void markAllRead(AuthenticatedUser admin) {
        notificationRepository.markAllRead(admin.getId(), Instant.now());
    }

    @Transactional
    public void clearAll(AuthenticatedUser admin) {
        notificationRepository.deleteAllForRecipient(admin.getId());
    }

    @Transactional
    public void notifyNewTicket(Ticket ticket) {
        boolean walkIn = ticket.getChannel() == TicketChannel.ONSITE_RFID;
        String title = walkIn ? "New walk-in ticket" : "New online ticket";
        String number = ticket.getTicketNumber() != null ? ticket.getTicketNumber() : "New ticket";
        String body = number + " · " + safe(ticket.getRequesterName()) + " · " + safe(ticket.getSubject());
        fanoutToAdmins(StaffNotificationType.NEW_TICKET, title, body, ticket, null);
    }

    @Transactional
    public void notifyRequesterMessage(Ticket ticket, AuthenticatedUser author, String rawBody) {
        if (author == null || author.getRole() != Role.USER) {
            return;
        }
        String preview = preview(rawBody);
        String number = ticket.getTicketNumber() != null ? ticket.getTicketNumber() : "Ticket";
        String authorName = author.getName() != null && !author.getName().isBlank()
                ? author.getName().trim()
                : author.getEmail();
        String body = number + " · " + authorName + (preview.isBlank() ? " sent a photo" : ": " + preview);
        Long assigned = ticket.getAssignedAdminId();
        if (assigned != null) {
            create(assigned, StaffNotificationType.NEW_MESSAGE, "New message", body, ticket);
            return;
        }
        fanoutToAdmins(StaffNotificationType.NEW_MESSAGE, "New message", body, ticket, null);
    }

    private void fanoutToAdmins(
            StaffNotificationType type,
            String title,
            String body,
            Ticket ticket,
            Long excludeUserId
    ) {
        for (User admin : userRepository.findByRoleIn(ADMIN_ROLES)) {
            if (!admin.isActive()) {
                continue;
            }
            if (excludeUserId != null && excludeUserId.equals(admin.getId())) {
                continue;
            }
            create(admin.getId(), type, title, body, ticket);
        }
    }

    private void create(
            Long recipientId,
            StaffNotificationType type,
            String title,
            String body,
            Ticket ticket
    ) {
        StaffNotification notification = new StaffNotification();
        notification.setRecipientId(recipientId);
        notification.setType(type);
        notification.setTitle(title);
        notification.setBody(clip(body, BODY_MAX));
        notification.setTicketId(ticket.getId());
        notification.setTicketChannel(ticket.getChannel() != null ? ticket.getChannel().name() : null);
        notification.setCreatedAt(Instant.now());
        notificationRepository.persist(notification);
    }

    private static String preview(String rawBody) {
        if (rawBody == null) {
            return "";
        }
        String trimmed = rawBody.trim().replaceAll("\\s+", " ");
        return clip(trimmed, 140);
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "—" : value.trim();
    }

    private static String clip(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }
}

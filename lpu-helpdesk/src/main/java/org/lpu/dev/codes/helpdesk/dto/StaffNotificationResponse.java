package org.lpu.dev.codes.helpdesk.dto;

import java.time.Instant;
import org.lpu.dev.codes.helpdesk.model.StaffNotification;

public record StaffNotificationResponse(
        Long id,
        String type,
        String title,
        String body,
        Long ticketId,
        String ticketChannel,
        boolean read,
        Instant createdAt
) {
    public static StaffNotificationResponse from(StaffNotification notification) {
        return new StaffNotificationResponse(
                notification.getId(),
                notification.getType().name(),
                notification.getTitle(),
                notification.getBody(),
                notification.getTicketId(),
                notification.getTicketChannel(),
                notification.getReadAt() != null,
                notification.getCreatedAt()
        );
    }
}

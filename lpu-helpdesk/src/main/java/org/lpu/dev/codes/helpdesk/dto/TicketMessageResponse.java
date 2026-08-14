package org.lpu.dev.codes.helpdesk.dto;

import java.time.Instant;
import org.lpu.dev.codes.helpdesk.model.TicketMessage;

public record TicketMessageResponse(
        Long id,
        Long ticketId,
        Long authorUserId,
        String authorEmail,
        String authorName,
        String authorRole,
        String body,
        boolean hasAttachment,
        String attachmentContentType,
        String attachmentOriginalName,
        Instant createdAt
) {
    public static TicketMessageResponse from(TicketMessage message) {
        return new TicketMessageResponse(
                message.getId(),
                message.getTicketId(),
                message.getAuthorUserId(),
                message.getAuthorEmail(),
                message.getAuthorName(),
                message.getAuthorRole(),
                message.getBody() != null ? message.getBody() : "",
                message.hasAttachment(),
                message.getAttachmentContentType(),
                message.getAttachmentOriginalName(),
                message.getCreatedAt()
        );
    }
}

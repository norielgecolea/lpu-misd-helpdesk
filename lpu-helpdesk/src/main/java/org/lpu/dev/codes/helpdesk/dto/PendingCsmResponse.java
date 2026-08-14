package org.lpu.dev.codes.helpdesk.dto;

import java.time.Instant;
import org.lpu.dev.codes.helpdesk.model.Ticket;
import org.lpu.dev.codes.helpdesk.service.CategoryLabelCache;

public record PendingCsmResponse(
        Long ticketId,
        String ticketNumber,
        String subject,
        String categoryLabel,
        String channel,
        Instant closedAt,
        Instant createdAt
) {
    public static PendingCsmResponse from(Ticket ticket) {
        Instant closedAt = ticket.getResolvedAt() != null ? ticket.getResolvedAt() : ticket.getUpdatedAt();
        return new PendingCsmResponse(
                ticket.getId(),
                ticket.getTicketNumber(),
                ticket.getSubject(),
                CategoryLabelCache.labelFor(ticket.getCategory()),
                ticket.getChannel().name(),
                closedAt,
                ticket.getCreatedAt()
        );
    }
}

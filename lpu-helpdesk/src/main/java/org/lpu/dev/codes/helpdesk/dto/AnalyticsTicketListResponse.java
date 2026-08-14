package org.lpu.dev.codes.helpdesk.dto;

import java.time.Instant;
import java.util.List;

public record AnalyticsTicketListResponse(
        String title,
        boolean truncated,
        int limit,
        List<Item> items
) {
    public record Item(
            Long id,
            String ticketNumber,
            String subject,
            String status,
            String category,
            String categoryLabel,
            String requesterName,
            String requesterEmail,
            String channel,
            Long assignedAdminId,
            String assignedAdminName,
            Instant createdAt,
            Instant resolvedAt,
            String csmRating,
            String csmComment,
            Instant csmSubmittedAt
    ) {
    }
}

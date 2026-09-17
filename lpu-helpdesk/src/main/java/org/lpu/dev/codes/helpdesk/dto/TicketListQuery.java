package org.lpu.dev.codes.helpdesk.dto;

import org.lpu.dev.codes.helpdesk.model.TicketChannel;
import org.lpu.dev.codes.helpdesk.model.TicketStatus;

public record TicketListQuery(
        TicketStatus status,
        TicketChannel channel,
        Long assignedAdminId,
        boolean unassignedOnly,
        String category,
        String subcategory,
        Long requesterUserId,
        String requesterEmail,
        String sort,
        boolean ascending,
        int offset,
        int limit
) {
    public static final int MAX_LIMIT = 200;

    public TicketListQuery {
        category = blankToNull(category);
        subcategory = blankToNull(subcategory);
        requesterEmail = blankToNull(requesterEmail);
        sort = sort == null || sort.isBlank() ? "updatedAt" : sort.trim();
        offset = Math.max(offset, 0);
        limit = Math.min(Math.max(limit, 1), MAX_LIMIT);
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}

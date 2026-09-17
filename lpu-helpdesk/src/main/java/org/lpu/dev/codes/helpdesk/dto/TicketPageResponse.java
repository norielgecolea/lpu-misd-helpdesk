package org.lpu.dev.codes.helpdesk.dto;

import java.util.List;

public record TicketPageResponse(
        List<TicketResponse> items,
        long total,
        long unreadTotal,
        long openCount,
        long inProgressCount
) {
}

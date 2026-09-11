package org.lpu.dev.codes.helpdesk.dto;

import java.util.List;

public record TicketMessagesResponse(
        List<TicketMessageResponse> messages,
        boolean requesterOnline,
        boolean staffOnline
) {
}

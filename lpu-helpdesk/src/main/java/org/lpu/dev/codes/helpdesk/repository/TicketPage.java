package org.lpu.dev.codes.helpdesk.repository;

import java.util.List;
import org.lpu.dev.codes.helpdesk.model.Ticket;

public record TicketPage(
        List<Ticket> items,
        long total,
        long unreadTotal,
        long openCount,
        long inProgressCount
) {
}

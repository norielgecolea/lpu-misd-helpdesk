package org.lpu.dev.codes.helpdesk.dto;

/** Kiosk create result: original concern plus an optional email-link ticket. */
public record KioskTicketCreateResponse(
        TicketResponse ticket,
        TicketResponse emailLinkTicket
) {
}

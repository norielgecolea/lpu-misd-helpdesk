package org.lpu.dev.codes.helpdesk.model;

/**
 * How the ticket was created. {@code ONSITE_RFID} is reserved for the
 * upcoming RFID-tap kiosk (not built yet) so the schema doesn't need to
 * change when that lands.
 */
public enum TicketChannel {
    ONLINE,
    ONSITE_RFID
}

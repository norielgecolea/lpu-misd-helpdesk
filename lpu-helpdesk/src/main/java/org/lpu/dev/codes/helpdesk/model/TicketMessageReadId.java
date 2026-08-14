package org.lpu.dev.codes.helpdesk.model;

import java.io.Serializable;
import java.util.Objects;

public class TicketMessageReadId implements Serializable {

    private Long userId;
    private Long ticketId;

    public TicketMessageReadId() {
    }

    public TicketMessageReadId(Long userId, Long ticketId) {
        this.userId = userId;
        this.ticketId = ticketId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getTicketId() {
        return ticketId;
    }

    public void setTicketId(Long ticketId) {
        this.ticketId = ticketId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TicketMessageReadId that)) {
            return false;
        }
        return Objects.equals(userId, that.userId) && Objects.equals(ticketId, that.ticketId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, ticketId);
    }
}

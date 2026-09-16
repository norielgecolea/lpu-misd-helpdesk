package org.lpu.dev.codes.helpdesk.model;

import java.time.Instant;

public class StaffNotification {

    private Long id;
    private Long recipientId;
    private StaffNotificationType type;
    private String title;
    private String body;
    private Long ticketId;
    private String ticketChannel;
    private Instant readAt;
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getRecipientId() {
        return recipientId;
    }

    public void setRecipientId(Long recipientId) {
        this.recipientId = recipientId;
    }

    public StaffNotificationType getType() {
        return type;
    }

    public void setType(StaffNotificationType type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public Long getTicketId() {
        return ticketId;
    }

    public void setTicketId(Long ticketId) {
        this.ticketId = ticketId;
    }

    public String getTicketChannel() {
        return ticketChannel;
    }

    public void setTicketChannel(String ticketChannel) {
        this.ticketChannel = ticketChannel;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public void setReadAt(Instant readAt) {
        this.readAt = readAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}

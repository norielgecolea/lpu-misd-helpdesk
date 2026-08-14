package org.lpu.dev.codes.helpdesk.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "queue_transfer_requests")
public class QueueTransferRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    @Column(name = "from_admin_id", nullable = false)
    private Long fromAdminId;

    @Column(name = "to_admin_id", nullable = false)
    private Long toAdminId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QueueTransferStatus status = QueueTransferStatus.PENDING;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTicketId() {
        return ticketId;
    }

    public void setTicketId(Long ticketId) {
        this.ticketId = ticketId;
    }

    public Long getFromAdminId() {
        return fromAdminId;
    }

    public void setFromAdminId(Long fromAdminId) {
        this.fromAdminId = fromAdminId;
    }

    public Long getToAdminId() {
        return toAdminId;
    }

    public void setToAdminId(Long toAdminId) {
        this.toAdminId = toAdminId;
    }

    public QueueTransferStatus getStatus() {
        return status;
    }

    public void setStatus(QueueTransferStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(Instant resolvedAt) {
        this.resolvedAt = resolvedAt;
    }
}

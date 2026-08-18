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
import java.time.ZoneId;

@Entity
@Table(name = "tickets")
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Human-readable reference e.g. "OL-2026-1" / "OS-2026-1"; set after insert once the id is known. */
    @Column(name = "ticket_number", unique = true, length = 30)
    private String ticketNumber;

    public static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Manila");

    public static String formatPublicNumber(TicketChannel channel, Instant createdAt, Long id) {
        String prefix = channel == TicketChannel.ONSITE_RFID ? "OS" : "OL";
        Instant when = createdAt != null ? createdAt : Instant.now();
        int year = when.atZone(DISPLAY_ZONE).getYear();
        return prefix + "-" + year + "-" + id;
    }

    /**
     * Nullable so onsite RFID-created tickets (no account) can later be
     * linked once the requester's synced record's email matches a user.
     */
    @Column(name = "requester_user_id")
    private Long requesterUserId;

    @Column(name = "requester_email", nullable = false, length = 255)
    private String requesterEmail;

    @Column(name = "requester_name", nullable = false, length = 150)
    private String requesterName;

    /** MISD staff currently handling this ticket; null while unassigned. */
    @Column(name = "assigned_admin_id")
    private Long assignedAdminId;

    /** Daily-reset sequential number for onsite (queued) tickets only; null for online tickets. */
    @Column(name = "queue_number")
    private Integer queueNumber;

    /** Stable category code from {@code ticket_categories.code}. */
    @Column(nullable = false, length = 40)
    private String category;

    @Column(nullable = false, length = 200)
    private String subject;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TicketStatus status = TicketStatus.OPEN;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TicketChannel channel = TicketChannel.ONLINE;

    /** Relative filename under pictures/ticket-ids for online ticket ID verification. */
    @Column(name = "id_photo_filename", length = 255)
    private String idPhotoFilename;

    /**
     * Root MIME Message-ID for the ticket's email thread. Set when the first
     * conversation message is copied to email so all later replies stay in one thread.
     */
    @Column(name = "email_thread_root_id", length = 255)
    private String emailThreadRootId;

    /** STUDENT or EMPLOYEE when created from RFID / directory lookup. */
    @Column(name = "requester_person_type", length = 20)
    private String requesterPersonType;

    /** Student number or employee number shown on the monitoring board. */
    @Column(name = "requester_person_no", length = 50)
    private String requesterPersonNo;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTicketNumber() {
        return ticketNumber;
    }

    public void setTicketNumber(String ticketNumber) {
        this.ticketNumber = ticketNumber;
    }

    public Long getRequesterUserId() {
        return requesterUserId;
    }

    public void setRequesterUserId(Long requesterUserId) {
        this.requesterUserId = requesterUserId;
    }

    public String getRequesterEmail() {
        return requesterEmail;
    }

    public void setRequesterEmail(String requesterEmail) {
        this.requesterEmail = requesterEmail;
    }

    public String getRequesterName() {
        return requesterName;
    }

    public void setRequesterName(String requesterName) {
        this.requesterName = requesterName;
    }

    public Long getAssignedAdminId() {
        return assignedAdminId;
    }

    public void setAssignedAdminId(Long assignedAdminId) {
        this.assignedAdminId = assignedAdminId;
    }

    public Integer getQueueNumber() {
        return queueNumber;
    }

    public void setQueueNumber(Integer queueNumber) {
        this.queueNumber = queueNumber;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public TicketStatus getStatus() {
        return status;
    }

    public void setStatus(TicketStatus status) {
        this.status = status;
    }

    public TicketChannel getChannel() {
        return channel;
    }

    public void setChannel(TicketChannel channel) {
        this.channel = channel;
    }

    public String getIdPhotoFilename() {
        return idPhotoFilename;
    }

    public void setIdPhotoFilename(String idPhotoFilename) {
        this.idPhotoFilename = idPhotoFilename;
    }

    public String getEmailThreadRootId() {
        return emailThreadRootId;
    }

    public void setEmailThreadRootId(String emailThreadRootId) {
        this.emailThreadRootId = emailThreadRootId;
    }

    public String getRequesterPersonType() {
        return requesterPersonType;
    }

    public void setRequesterPersonType(String requesterPersonType) {
        this.requesterPersonType = requesterPersonType;
    }

    public String getRequesterPersonNo() {
        return requesterPersonNo;
    }

    public void setRequesterPersonNo(String requesterPersonNo) {
        this.requesterPersonNo = requesterPersonNo;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(Instant resolvedAt) {
        this.resolvedAt = resolvedAt;
    }
}

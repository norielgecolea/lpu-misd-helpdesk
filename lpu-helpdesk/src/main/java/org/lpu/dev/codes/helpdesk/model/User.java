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
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String email;

    /** Staff login handle (ADMIN / SUPER_ADMIN / MONITORING). Null for student USER accounts. */
    @Column(length = 50)
    private String username;

    @Column(nullable = false, length = 150)
    private String name;

    /** Only set for ADMIN/SUPER_ADMIN accounts; USER accounts sign in via Microsoft/OTP and never have one. */
    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role = Role.USER;

    @Column(nullable = false)
    private boolean active = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "id_verification_status", nullable = false, length = 20)
    private IdVerificationStatus idVerificationStatus = IdVerificationStatus.NONE;

    /** Stored filename under {@code pictures/id-verifications/}; never a client-supplied path. */
    @Column(name = "id_photo_filename", length = 255)
    private String idPhotoFilename;

    @Column(name = "id_uploaded_at")
    private Instant idUploadedAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public IdVerificationStatus getIdVerificationStatus() {
        return idVerificationStatus;
    }

    public void setIdVerificationStatus(IdVerificationStatus idVerificationStatus) {
        this.idVerificationStatus = idVerificationStatus;
    }

    public String getIdPhotoFilename() {
        return idPhotoFilename;
    }

    public void setIdPhotoFilename(String idPhotoFilename) {
        this.idPhotoFilename = idPhotoFilename;
    }

    public Instant getIdUploadedAt() {
        return idUploadedAt;
    }

    public void setIdUploadedAt(Instant idUploadedAt) {
        this.idUploadedAt = idUploadedAt;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(Instant lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
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
}

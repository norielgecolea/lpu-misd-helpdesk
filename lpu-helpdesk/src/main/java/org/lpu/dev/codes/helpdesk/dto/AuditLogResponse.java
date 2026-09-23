package org.lpu.dev.codes.helpdesk.dto;

import java.time.Instant;
import org.lpu.dev.codes.helpdesk.model.AuditLog;

public record AuditLogResponse(
        Long id,
        Long actorId,
        String actorEmail,
        String actorName,
        String actorRole,
        String action,
        String actionLabel,
        String resourceType,
        String resourceId,
        String resourceLabel,
        String summary,
        String details,
        Instant createdAt
) {
    public static AuditLogResponse from(AuditLog log) {
        return new AuditLogResponse(
                log.getId(),
                log.getActorId(),
                log.getActorEmail(),
                log.getActorName(),
                log.getActorRole(),
                log.getAction() != null ? log.getAction().name() : null,
                log.getAction() != null ? log.getAction().label() : null,
                log.getResourceType() != null ? log.getResourceType().name() : null,
                log.getResourceId(),
                log.getResourceLabel(),
                log.getSummary(),
                log.getDetails(),
                log.getCreatedAt()
        );
    }
}

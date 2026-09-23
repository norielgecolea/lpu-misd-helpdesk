package org.lpu.dev.codes.helpdesk.dto;

import java.time.Instant;
import org.lpu.dev.codes.helpdesk.model.AuditAction;
import org.lpu.dev.codes.helpdesk.model.AuditResourceType;

public record AuditLogQuery(
        String search,
        AuditAction action,
        AuditResourceType resourceType,
        Instant from,
        Instant to,
        int offset,
        int limit
) {
}

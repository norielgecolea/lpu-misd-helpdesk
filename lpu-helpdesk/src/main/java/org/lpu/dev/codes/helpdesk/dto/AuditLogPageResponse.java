package org.lpu.dev.codes.helpdesk.dto;

import java.util.List;

public record AuditLogPageResponse(List<AuditLogResponse> items, long total) {
}

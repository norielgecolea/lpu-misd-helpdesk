package org.lpu.dev.codes.helpdesk.controller;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import org.lpu.dev.codes.helpdesk.dto.AuditLogPageResponse;
import org.lpu.dev.codes.helpdesk.dto.AuditLogQuery;
import org.lpu.dev.codes.helpdesk.model.AuditAction;
import org.lpu.dev.codes.helpdesk.model.AuditResourceType;
import org.lpu.dev.codes.helpdesk.model.Ticket;
import org.lpu.dev.codes.helpdesk.service.AuditLogService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin/audit-logs")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminAuditLogController {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 100;
    private static final ZoneId ZONE = Ticket.DISPLAY_ZONE;

    private final AuditLogService auditLogService;

    public AdminAuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public ResponseEntity<AuditLogPageResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "50") int limit
    ) {
        if (offset < 0) {
            offset = 0;
        }
        if (limit <= 0) {
            limit = DEFAULT_LIMIT;
        }
        if (limit > MAX_LIMIT) {
            limit = MAX_LIMIT;
        }
        Instant fromInstant = parseStart(from);
        Instant toInstant = parseEndExclusive(to);
        if (fromInstant != null && toInstant != null && toInstant.isBefore(fromInstant)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "'to' must be on or after 'from'");
        }
        AuditLogQuery query = new AuditLogQuery(
                q,
                parseAction(action),
                parseResourceType(resourceType),
                fromInstant,
                toInstant,
                offset,
                limit
        );
        return ResponseEntity.ok(auditLogService.page(query));
    }

    private static AuditAction parseAction(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return AuditAction.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown audit action");
        }
    }

    private static AuditResourceType parseResourceType(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return AuditResourceType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown resource type");
        }
    }

    private static Instant parseStart(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            if (raw.length() <= 10) {
                return LocalDate.parse(raw).atStartOfDay(ZONE).toInstant();
            }
            return Instant.parse(raw);
        } catch (DateTimeParseException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid 'from' date");
        }
    }

    private static Instant parseEndExclusive(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            if (raw.length() <= 10) {
                return LocalDate.parse(raw).plusDays(1).atStartOfDay(ZONE).toInstant();
            }
            return Instant.parse(raw);
        } catch (DateTimeParseException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid 'to' date");
        }
    }
}

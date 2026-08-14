package org.lpu.dev.codes.helpdesk.controller;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import org.lpu.dev.codes.helpdesk.dto.AnalyticsCsmByAssigneeResponse;
import org.lpu.dev.codes.helpdesk.dto.AnalyticsSummaryResponse;
import org.lpu.dev.codes.helpdesk.dto.AnalyticsTicketListResponse;
import org.lpu.dev.codes.helpdesk.model.CsmRating;
import org.lpu.dev.codes.helpdesk.service.AnalyticsService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin/analytics")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public class AdminAnalyticsController {

    private final AnalyticsService analyticsService;

    public AdminAnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/summary")
    public ResponseEntity<AnalyticsSummaryResponse> summary(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to
    ) {
        Instant fromInstant = parseStart(from, Instant.now().minus(30, ChronoUnit.DAYS));
        Instant toInstant = parseEndExclusive(to, Instant.now().plus(1, ChronoUnit.DAYS));
        if (toInstant.isBefore(fromInstant)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "'to' must be on or after 'from'");
        }
        return ResponseEntity.ok(analyticsService.summarize(fromInstant, toInstant));
    }

    @GetMapping("/assignee-tickets")
    public ResponseEntity<AnalyticsTicketListResponse> assigneeTickets(
            @RequestParam Long adminId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to
    ) {
        Instant fromInstant = parseStart(from, Instant.now().minus(30, ChronoUnit.DAYS));
        Instant toInstant = parseEndExclusive(to, Instant.now().plus(1, ChronoUnit.DAYS));
        if (toInstant.isBefore(fromInstant)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "'to' must be on or after 'from'");
        }
        return ResponseEntity.ok(analyticsService.assigneeTickets(adminId, fromInstant, toInstant));
    }

    @GetMapping("/csm-tickets")
    public ResponseEntity<AnalyticsTicketListResponse> csmTickets(
            @RequestParam String rating,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Long adminId
    ) {
        CsmRating parsed;
        try {
            parsed = CsmRating.valueOf(rating.trim().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "rating must be SAD, NEUTRAL, or HAPPY");
        }
        Instant fromInstant = parseStart(from, Instant.now().minus(30, ChronoUnit.DAYS));
        Instant toInstant = parseEndExclusive(to, Instant.now().plus(1, ChronoUnit.DAYS));
        if (toInstant.isBefore(fromInstant)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "'to' must be on or after 'from'");
        }
        return ResponseEntity.ok(analyticsService.csmTickets(parsed, fromInstant, toInstant, adminId));
    }

    @GetMapping("/csm-by-assignee")
    public ResponseEntity<AnalyticsCsmByAssigneeResponse> csmByAssignee(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to
    ) {
        Instant fromInstant = parseStart(from, Instant.now().minus(30, ChronoUnit.DAYS));
        Instant toInstant = parseEndExclusive(to, Instant.now().plus(1, ChronoUnit.DAYS));
        if (toInstant.isBefore(fromInstant)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "'to' must be on or after 'from'");
        }
        return ResponseEntity.ok(analyticsService.csmByAssignee(fromInstant, toInstant));
    }

    private static Instant parseStart(String raw, Instant fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback.atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant();
        }
        try {
            if (raw.length() <= 10) {
                return LocalDate.parse(raw).atStartOfDay(ZoneOffset.UTC).toInstant();
            }
            return Instant.parse(raw);
        } catch (DateTimeParseException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid 'from' date");
        }
    }

    /** End of selected calendar day (exclusive next midnight UTC). */
    private static Instant parseEndExclusive(String raw, Instant fallback) {
        if (raw == null || raw.isBlank()) {
            return LocalDate.now(ZoneOffset.UTC).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        }
        try {
            if (raw.length() <= 10) {
                return LocalDate.parse(raw).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
            }
            return Instant.parse(raw);
        } catch (DateTimeParseException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid 'to' date");
        }
    }
}

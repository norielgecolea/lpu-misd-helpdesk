package org.lpu.dev.codes.helpdesk.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lpu.dev.codes.helpdesk.dto.AuditLogPageResponse;
import org.lpu.dev.codes.helpdesk.dto.AuditLogQuery;
import org.lpu.dev.codes.helpdesk.dto.AuditLogResponse;
import org.lpu.dev.codes.helpdesk.model.AuditAction;
import org.lpu.dev.codes.helpdesk.model.AuditLog;
import org.lpu.dev.codes.helpdesk.model.User;
import org.lpu.dev.codes.helpdesk.repository.AuditLogRepository;
import org.lpu.dev.codes.helpdesk.security.AuthenticatedUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditLogService {

    private static final Logger log = LogManager.getLogger(AuditLogService.class);

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(readOnly = true)
    public AuditLogPageResponse page(AuditLogQuery query) {
        AuditLogRepository.Page page = auditLogRepository.page(query);
        return new AuditLogPageResponse(
                page.items().stream().map(AuditLogResponse::from).toList(),
                page.total()
        );
    }

    @Transactional
    public void record(AuditAction action, String resourceId, String resourceLabel, String summary) {
        record(action, resourceId, resourceLabel, summary, null);
    }

    @Transactional
    public void record(AuditAction action, String resourceId, String resourceLabel, String summary, String details) {
        persist(currentActor(), action, resourceId, resourceLabel, summary, details);
    }

    @Transactional
    public void record(AuthenticatedUser actor, AuditAction action, String resourceId, String resourceLabel, String summary) {
        record(actor, action, resourceId, resourceLabel, summary, null);
    }

    @Transactional
    public void record(
            AuthenticatedUser actor,
            AuditAction action,
            String resourceId,
            String resourceLabel,
            String summary,
            String details
    ) {
        persist(actor, action, resourceId, resourceLabel, summary, details);
    }

    @Transactional
    public void record(User actor, AuditAction action, String resourceId, String resourceLabel, String summary) {
        persist(fromUser(actor), action, resourceId, resourceLabel, summary, null);
    }

    @Transactional
    public void recordSystem(AuditAction action, String resourceId, String resourceLabel, String summary) {
        persist(systemActor(), action, resourceId, resourceLabel, summary, null);
    }

    /**
     * Commits even if the surrounding work later fails — used for failed sign-ins.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordIndependent(
            AuthenticatedUser actor,
            AuditAction action,
            String resourceId,
            String resourceLabel,
            String summary
    ) {
        try {
            persist(actor, action, resourceId, resourceLabel, summary, null);
        } catch (RuntimeException ex) {
            log.error("Failed to write independent audit log action={}", action, ex);
        }
    }

    public static String clip(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= max) {
            return trimmed.isEmpty() ? null : trimmed;
        }
        return trimmed.substring(0, Math.max(0, max - 1)) + "…";
    }

    public static String changes(String... labelsAndValues) {
        if (labelsAndValues == null || labelsAndValues.length < 3) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        for (int i = 0; i + 2 < labelsAndValues.length; i += 3) {
            String label = labelsAndValues[i];
            String from = blankToDash(labelsAndValues[i + 1]);
            String to = blankToDash(labelsAndValues[i + 2]);
            if (from.equals(to)) {
                continue;
            }
            parts.add(label + ": " + from + " → " + to);
        }
        return parts.isEmpty() ? null : String.join("; ", parts);
    }

    private void persist(
            AuthenticatedUser actor,
            AuditAction action,
            String resourceId,
            String resourceLabel,
            String summary,
            String details
    ) {
        AuditLog entry = new AuditLog();
        if (actor != null) {
            entry.setActorId(actor.getId());
            entry.setActorEmail(clip(actor.getEmail(), 255));
            entry.setActorName(clip(actor.getName(), 150));
            if (actor.getRole() != null) {
                entry.setActorRole(actor.getRole().name());
            } else if ("System".equals(actor.getName())) {
                entry.setActorRole("SYSTEM");
            }
        }
        entry.setAction(action);
        entry.setResourceType(action.resourceType());
        entry.setResourceId(clip(resourceId, 80));
        entry.setResourceLabel(clip(resourceLabel, 200));
        String resolvedSummary = clip(summary, 500);
        entry.setSummary(resolvedSummary != null ? resolvedSummary : action.label());
        entry.setDetails(clip(details, 4000));
        entry.setCreatedAt(Instant.now());
        auditLogRepository.persist(entry);
    }

    private AuthenticatedUser currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return user;
        }
        return null;
    }

    private static AuthenticatedUser fromUser(User user) {
        if (user == null) {
            return null;
        }
        return new AuthenticatedUser(user.getId(), user.getEmail(), user.getName(), user.getRole());
    }

    private static AuthenticatedUser systemActor() {
        return new AuthenticatedUser(null, "system", "System", null);
    }

    private static String blankToDash(String value) {
        if (value == null || value.isBlank()) {
            return "—";
        }
        return value.trim();
    }
}

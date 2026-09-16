package org.lpu.dev.codes.helpdesk.controller;

import org.lpu.dev.codes.helpdesk.dto.StaffNotificationListResponse;
import org.lpu.dev.codes.helpdesk.dto.StaffNotificationResponse;
import org.lpu.dev.codes.helpdesk.security.AuthenticatedUser;
import org.lpu.dev.codes.helpdesk.service.StaffNotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/notifications")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public class StaffNotificationController {

    private final StaffNotificationService staffNotificationService;

    public StaffNotificationController(StaffNotificationService staffNotificationService) {
        this.staffNotificationService = staffNotificationService;
    }

    @GetMapping
    public ResponseEntity<StaffNotificationListResponse> list(
            @AuthenticationPrincipal AuthenticatedUser admin
    ) {
        return ResponseEntity.ok(staffNotificationService.listFor(admin));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<StaffNotificationResponse> markRead(
            @AuthenticationPrincipal AuthenticatedUser admin,
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(staffNotificationService.markRead(admin, id));
    }

    @PostMapping("/read-all")
    public ResponseEntity<Void> markAllRead(@AuthenticationPrincipal AuthenticatedUser admin) {
        staffNotificationService.markAllRead(admin);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> clearAll(@AuthenticationPrincipal AuthenticatedUser admin) {
        staffNotificationService.clearAll(admin);
        return ResponseEntity.noContent().build();
    }
}

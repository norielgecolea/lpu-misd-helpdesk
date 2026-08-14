package org.lpu.dev.codes.helpdesk.controller;

import jakarta.validation.Valid;
import java.util.List;
import org.lpu.dev.codes.helpdesk.dto.AdminAccountResponse;
import org.lpu.dev.codes.helpdesk.dto.CreateAdminRequest;
import org.lpu.dev.codes.helpdesk.dto.SetActiveRequest;
import org.lpu.dev.codes.helpdesk.model.User;
import org.lpu.dev.codes.helpdesk.security.AuthenticatedUser;
import org.lpu.dev.codes.helpdesk.service.AdminAccountService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/accounts")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminAccountController {

    private final AdminAccountService adminAccountService;

    public AdminAccountController(AdminAccountService adminAccountService) {
        this.adminAccountService = adminAccountService;
    }

    @GetMapping
    public ResponseEntity<List<AdminAccountResponse>> list() {
        List<AdminAccountResponse> admins = adminAccountService.listAdmins().stream()
                .map(AdminAccountResponse::from)
                .toList();
        return ResponseEntity.ok(admins);
    }

    @PostMapping
    public ResponseEntity<AdminAccountResponse> create(
            @AuthenticationPrincipal AuthenticatedUser actingAdmin,
            @Valid @RequestBody CreateAdminRequest request
    ) {
        User created = adminAccountService.createAdmin(actingAdmin, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(AdminAccountResponse.from(created));
    }

    @PatchMapping("/{id}/active")
    public ResponseEntity<AdminAccountResponse> setActive(
            @AuthenticationPrincipal AuthenticatedUser actingAdmin,
            @PathVariable Long id,
            @Valid @RequestBody SetActiveRequest request
    ) {
        User updated = adminAccountService.setActive(actingAdmin, id, request.active());
        return ResponseEntity.ok(AdminAccountResponse.from(updated));
    }
}

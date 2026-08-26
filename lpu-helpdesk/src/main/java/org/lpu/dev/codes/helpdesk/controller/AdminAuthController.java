package org.lpu.dev.codes.helpdesk.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import org.lpu.dev.codes.helpdesk.dto.AdminLoginRequest;
import org.lpu.dev.codes.helpdesk.dto.ChangePasswordRequest;
import org.lpu.dev.codes.helpdesk.dto.ForgotPasswordRequest;
import org.lpu.dev.codes.helpdesk.dto.LoginResponse;
import org.lpu.dev.codes.helpdesk.dto.ResetPasswordRequest;
import org.lpu.dev.codes.helpdesk.security.AuthenticatedUser;
import org.lpu.dev.codes.helpdesk.service.AdminAuthService;
import org.lpu.dev.codes.helpdesk.service.AdminPasswordService;
import org.lpu.dev.codes.helpdesk.service.TurnstileVerificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/auth")
public class AdminAuthController {

    private static final String ADMIN_LOGIN_ACTION = "admin-login";
    private static final String FORGOT_PASSWORD_ACTION = "forgot-password";

    private final AdminAuthService adminAuthService;
    private final AdminPasswordService adminPasswordService;
    private final TurnstileVerificationService turnstileVerificationService;

    public AdminAuthController(
            AdminAuthService adminAuthService,
            AdminPasswordService adminPasswordService,
            TurnstileVerificationService turnstileVerificationService
    ) {
        this.adminAuthService = adminAuthService;
        this.adminPasswordService = adminPasswordService;
        this.turnstileVerificationService = turnstileVerificationService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody AdminLoginRequest request,
            HttpServletRequest httpRequest
    ) {
        turnstileVerificationService.verify(request.turnstileResponse(), ADMIN_LOGIN_ACTION, httpRequest);
        boolean rememberMe = Boolean.TRUE.equals(request.rememberMe());
        return ResponseEntity.ok(adminAuthService.login(request.login(), request.password(), rememberMe));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request,
            HttpServletRequest httpRequest
    ) {
        turnstileVerificationService.verify(request.turnstileResponse(), FORGOT_PASSWORD_ACTION, httpRequest);
        adminPasswordService.requestPasswordReset(request.login());
        return ResponseEntity.ok(Map.of(
                "message",
                "If an account exists for that username or email, a reset link has been sent."
        ));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        adminPasswordService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.ok(Map.of("message", "Password updated. You can sign in with your new password."));
    }

    @PostMapping("/change-password")
    public ResponseEntity<Map<String, String>> changePassword(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        adminPasswordService.changePassword(user, request.currentPassword(), request.newPassword());
        return ResponseEntity.ok(Map.of("message", "Password changed successfully."));
    }
}

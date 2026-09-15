package org.lpu.dev.codes.helpdesk.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.Map;
import org.lpu.dev.codes.helpdesk.dto.GoogleLoginRequest;
import org.lpu.dev.codes.helpdesk.dto.LoginResponse;
import org.lpu.dev.codes.helpdesk.dto.MicrosoftLoginRequest;
import org.lpu.dev.codes.helpdesk.dto.OtpRequestRequest;
import org.lpu.dev.codes.helpdesk.dto.OtpRequestResponse;
import org.lpu.dev.codes.helpdesk.dto.OtpVerifyRequest;
import org.lpu.dev.codes.helpdesk.security.AuthenticatedUser;
import org.lpu.dev.codes.helpdesk.service.AuthService;
import org.lpu.dev.codes.helpdesk.service.TurnstileVerificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String OTP_REQUEST_ACTION = "otp-request";

    private final AuthService authService;
    private final TurnstileVerificationService turnstileVerificationService;

    public AuthController(AuthService authService, TurnstileVerificationService turnstileVerificationService) {
        this.authService = authService;
        this.turnstileVerificationService = turnstileVerificationService;
    }

    @PostMapping("/microsoft")
    public ResponseEntity<LoginResponse> loginWithMicrosoft(@Valid @RequestBody MicrosoftLoginRequest request) {
        return ResponseEntity.ok(authService.loginWithMicrosoft(request.idToken()));
    }

    @PostMapping("/google")
    public ResponseEntity<LoginResponse> loginWithGoogle(@Valid @RequestBody GoogleLoginRequest request) {
        return ResponseEntity.ok(authService.loginWithGoogle(request.idToken(), request.nonce()));
    }

    @GetMapping("/google/config")
    public ResponseEntity<Map<String, Object>> googleConfig() {
        return ResponseEntity.ok(authService.googleLoginConfig());
    }

    @PostMapping("/otp/request")
    public ResponseEntity<OtpRequestResponse> requestOtp(
            @Valid @RequestBody OtpRequestRequest request,
            HttpServletRequest httpRequest
    ) {
        turnstileVerificationService.verify(request.turnstileResponse(), OTP_REQUEST_ACTION, httpRequest);
        return ResponseEntity.ok(authService.requestOtp(request.email()));
    }

    @PostMapping("/otp/verify")
    public ResponseEntity<LoginResponse> verifyOtp(@Valid @RequestBody OtpVerifyRequest request) {
        return ResponseEntity.ok(authService.verifyOtp(request.email(), request.code()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(@AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(Map.of("message", "Logged out"));
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, String>> me(@AuthenticationPrincipal AuthenticatedUser user) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("email", user.getEmail());
        body.put("name", user.getName());
        body.put("role", user.getRole().name());
        return ResponseEntity.ok(body);
    }
}

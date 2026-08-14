package org.lpu.dev.codes.helpdesk.controller;

import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.Map;
import org.lpu.dev.codes.helpdesk.dto.LoginResponse;
import org.lpu.dev.codes.helpdesk.dto.MicrosoftLoginRequest;
import org.lpu.dev.codes.helpdesk.dto.OtpRequestRequest;
import org.lpu.dev.codes.helpdesk.dto.OtpRequestResponse;
import org.lpu.dev.codes.helpdesk.dto.OtpVerifyRequest;
import org.lpu.dev.codes.helpdesk.security.AuthenticatedUser;
import org.lpu.dev.codes.helpdesk.service.AuthService;
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

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/microsoft")
    public ResponseEntity<LoginResponse> loginWithMicrosoft(@Valid @RequestBody MicrosoftLoginRequest request) {
        return ResponseEntity.ok(authService.loginWithMicrosoft(request.idToken()));
    }

    @PostMapping("/otp/request")
    public ResponseEntity<OtpRequestResponse> requestOtp(@Valid @RequestBody OtpRequestRequest request) {
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

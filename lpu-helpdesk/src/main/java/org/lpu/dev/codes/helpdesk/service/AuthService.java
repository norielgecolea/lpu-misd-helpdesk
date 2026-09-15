package org.lpu.dev.codes.helpdesk.service;

import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lpu.dev.codes.helpdesk.config.AuthProperties;
import org.lpu.dev.codes.helpdesk.dto.LoginResponse;
import org.lpu.dev.codes.helpdesk.dto.OtpRequestResponse;
import org.lpu.dev.codes.helpdesk.model.Role;
import org.lpu.dev.codes.helpdesk.model.User;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private static final Logger log = LogManager.getLogger(AuthService.class);

    private final UserService userService;
    private final JwtService jwtService;
    private final OtpService otpService;
    private final MicrosoftTokenService microsoftTokenService;
    private final GoogleTokenService googleTokenService;
    private final AuthProperties authProperties;

    public AuthService(
            UserService userService,
            JwtService jwtService,
            OtpService otpService,
            MicrosoftTokenService microsoftTokenService,
            GoogleTokenService googleTokenService,
            AuthProperties authProperties
    ) {
        this.userService = userService;
        this.jwtService = jwtService;
        this.otpService = otpService;
        this.microsoftTokenService = microsoftTokenService;
        this.googleTokenService = googleTokenService;
        this.authProperties = authProperties;
    }

    @Transactional
    public LoginResponse loginWithMicrosoft(String idToken) {
        MicrosoftTokenService.MicrosoftIdentity identity = microsoftTokenService.validate(idToken);
        requireAllowedDomain(identity.email());

        User user = userService.findOrCreateByEmail(identity.email(), identity.name());
        log.info("Microsoft login success for email={}", user.getEmail());
        return issueLoginResponse(user);
    }

    @Transactional
    public LoginResponse loginWithGoogle(String idToken, String nonce) {
        // Any verified Google account (campus or personal). Microsoft stays campus-only.
        GoogleTokenService.GoogleIdentity identity = googleTokenService.validate(idToken, nonce);

        User user = userService.findOrCreateByEmail(identity.email(), identity.name());
        log.info("Google login success for email={}", user.getEmail());
        return issueLoginResponse(user);
    }

    public Map<String, Object> googleLoginConfig() {
        boolean configured = googleTokenService.isConfigured();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("configured", configured);
        body.put("clientId", configured ? googleTokenService.clientId() : "");
        return body;
    }

    @Transactional
    public OtpRequestResponse requestOtp(String email) {
        // OTP accepts any valid email (including outside campus). Microsoft stays campus-only.
        long expiresInMs = otpService.requestOtp(email);
        log.info("OTP requested for email={}", email.trim().toLowerCase());
        return new OtpRequestResponse(expiresInMs);
    }

    @Transactional
    public LoginResponse verifyOtp(String email, String code) {
        // OTP accepts any valid email (including outside campus). Microsoft stays campus-only.
        boolean valid = otpService.verifyOtp(email, code);
        if (!valid) {
            log.warn("OTP verification failed for email={}", email.trim().toLowerCase());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired code");
        }

        User user = userService.findOrCreateByEmail(email, null);
        log.info("OTP login success for email={}", user.getEmail());
        return issueLoginResponse(user);
    }

    private LoginResponse issueLoginResponse(User user) {
        String token = jwtService.generateToken(user);
        boolean needsStudentInfo = needsStudentInfo(user);
        return new LoginResponse(
                user.getId(),
                token,
                "Bearer",
                user.getEmail(),
                user.getName(),
                user.getRole().name(),
                jwtService.getExpirationMs(),
                needsStudentInfo,
                user.getDeclaredStudentName(),
                user.getDeclaredStudentNo(),
                user.getDeclaredPersonType(),
                user.getDeclaredLpuEmail()
        );
    }

    private boolean needsStudentInfo(User user) {
        if (user.getRole() != Role.USER) {
            return false;
        }
        if (authProperties.isAllowedEmail(user.getEmail())) {
            return false;
        }
        String name = user.getDeclaredStudentName();
        String no = user.getDeclaredStudentNo();
        String type = user.getDeclaredPersonType();
        String lpuEmail = user.getDeclaredLpuEmail();
        return name == null || name.isBlank()
                || no == null || no.isBlank()
                || type == null || type.isBlank()
                || lpuEmail == null || lpuEmail.isBlank();
    }

    private void requireAllowedDomain(String email) {
        if (!authProperties.isAllowedEmail(email)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Only " + authProperties.allowedDomainsDisplay() + " accounts may sign in with Microsoft"
            );
        }
    }
}

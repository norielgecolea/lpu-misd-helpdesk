package org.lpu.dev.codes.helpdesk.service;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.EnumSet;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lpu.dev.codes.helpdesk.config.MailProperties;
import org.lpu.dev.codes.helpdesk.model.PasswordResetToken;
import org.lpu.dev.codes.helpdesk.model.Role;
import org.lpu.dev.codes.helpdesk.model.User;
import org.lpu.dev.codes.helpdesk.repository.PasswordResetTokenRepository;
import org.lpu.dev.codes.helpdesk.repository.UserRepository;
import org.lpu.dev.codes.helpdesk.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminPasswordService {

    private static final Logger log = LogManager.getLogger(AdminPasswordService.class);
    private static final Set<Role> STAFF_ROLES = EnumSet.of(Role.ADMIN, Role.SUPER_ADMIN, Role.MONITORING);
    private static final int RESET_EXPIRES_MINUTES = 30;

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminAuthEmailService adminAuthEmailService;
    private final MailProperties mailProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    public AdminPasswordService(
            UserRepository userRepository,
            PasswordResetTokenRepository tokenRepository,
            PasswordEncoder passwordEncoder,
            AdminAuthEmailService adminAuthEmailService,
            MailProperties mailProperties
    ) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminAuthEmailService = adminAuthEmailService;
        this.mailProperties = mailProperties;
    }

    /**
     * Always succeeds from the caller's perspective to avoid account enumeration.
     */
    @Transactional
    public void requestPasswordReset(String login) {
        String normalized = login == null ? "" : login.trim().toLowerCase();
        if (normalized.isBlank()) {
            return;
        }
        User user = userRepository.findStaffByEmailOrUsername(normalized).orElse(null);
        if (user == null || !STAFF_ROLES.contains(user.getRole()) || !user.isActive()) {
            log.info("Password reset requested for unknown/inactive login={}", normalized);
            return;
        }
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            log.warn("Password reset skipped — staff account has no email userId={}", user.getId());
            return;
        }

        tokenRepository.invalidateActiveByUserId(user.getId());
        String rawToken = generateToken();
        PasswordResetToken token = new PasswordResetToken();
        token.setUserId(user.getId());
        token.setTokenHash(passwordEncoder.encode(rawToken));
        token.setExpiresAt(Instant.now().plus(RESET_EXPIRES_MINUTES, ChronoUnit.MINUTES));
        tokenRepository.persist(token);

        String resetUrl = mailProperties.trimmedAdminBaseUrl() + "/admin/reset-password?token=" + rawToken;
        adminAuthEmailService.sendPasswordResetEmail(
                user.getEmail(),
                user.getName(),
                resetUrl,
                RESET_EXPIRES_MINUTES
        );
        log.info("Password reset token created for userId={}", user.getId());
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reset token is required");
        }
        requireStrongPassword(newPassword);

        PasswordResetToken matched = null;
        for (PasswordResetToken candidate : tokenRepository.findUnconsumedNotExpired(Instant.now())) {
            if (passwordEncoder.matches(rawToken.trim(), candidate.getTokenHash())) {
                matched = candidate;
                break;
            }
        }
        if (matched == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This reset link is invalid or has expired");
        }

        User user = userRepository.findById(matched.getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Account not found"));
        if (!STAFF_ROLES.contains(user.getRole()) || !user.isActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This account cannot reset its password");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);

        matched.setConsumed(true);
        tokenRepository.save(matched);
        tokenRepository.invalidateActiveByUserId(user.getId());
        log.info("Password reset completed for userId={}", user.getId());
    }

    @Transactional
    public void changePassword(AuthenticatedUser acting, String currentPassword, String newPassword) {
        User user = userRepository.findById(acting.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        if (!STAFF_ROLES.contains(user.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not a staff account");
        }
        if (user.getPasswordHash() == null || !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
        }
        requireStrongPassword(newPassword);
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "New password must be different from the current password");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);
        tokenRepository.invalidateActiveByUserId(user.getId());
        log.info("Password changed for userId={}", user.getId());
    }

    private static void requireStrongPassword(String password) {
        if (password == null || password.length() < 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be at least 8 characters");
        }
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}

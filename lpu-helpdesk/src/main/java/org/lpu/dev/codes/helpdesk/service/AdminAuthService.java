package org.lpu.dev.codes.helpdesk.service;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lpu.dev.codes.helpdesk.dto.LoginResponse;
import org.lpu.dev.codes.helpdesk.model.Role;
import org.lpu.dev.codes.helpdesk.model.User;
import org.lpu.dev.codes.helpdesk.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminAuthService {

    private static final Logger log = LogManager.getLogger(AdminAuthService.class);
    private static final Set<Role> STAFF_ROLES = EnumSet.of(Role.ADMIN, Role.SUPER_ADMIN, Role.MONITORING);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AdminAuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public LoginResponse login(String login, String password, boolean rememberMe) {
        String normalizedLogin = login.trim().toLowerCase();
        User user = userRepository.findStaffByEmailOrUsername(normalizedLogin).orElse(null);

        if (user == null || !STAFF_ROLES.contains(user.getRole())
                || user.getPasswordHash() == null
                || !passwordEncoder.matches(password, user.getPasswordHash())) {
            log.warn("Admin login failed for login={}", normalizedLogin);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username/email or password");
        }

        if (!user.isActive()) {
            log.warn("Admin login blocked for inactive account login={}", normalizedLogin);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This account has been deactivated");
        }

        user.setLastLoginAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);

        log.info("Admin login success for email={} role={}", user.getEmail(), user.getRole());

        String token = jwtService.generateToken(user, rememberMe);
        return new LoginResponse(
                user.getId(),
                token,
                "Bearer",
                user.getEmail(),
                user.getName(),
                user.getRole().name(),
                jwtService.getExpirationMs(rememberMe)
        );
    }
}

package org.lpu.dev.codes.helpdesk.service;

import java.time.Instant;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lpu.dev.codes.helpdesk.config.AuthProperties;
import org.lpu.dev.codes.helpdesk.dto.CreateAdminRequest;
import org.lpu.dev.codes.helpdesk.model.Role;
import org.lpu.dev.codes.helpdesk.model.User;
import org.lpu.dev.codes.helpdesk.repository.UserRepository;
import org.lpu.dev.codes.helpdesk.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Super-Admin-only management of staff accounts (ADMIN / SUPER_ADMIN / MONITORING). */
@Service
public class AdminAccountService {

    private static final Logger log = LogManager.getLogger(AdminAccountService.class);
    private static final List<Role> STAFF_ROLES = List.of(Role.ADMIN, Role.SUPER_ADMIN, Role.MONITORING);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthProperties authProperties;

    public AdminAccountService(UserRepository userRepository, PasswordEncoder passwordEncoder, AuthProperties authProperties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authProperties = authProperties;
    }

    @Transactional
    public User createAdmin(AuthenticatedUser actingAdmin, CreateAdminRequest request) {
        String email = request.email().trim().toLowerCase();
        String username = request.username().trim().toLowerCase();
        requireAllowedDomain(email);
        requireValidUsername(username);

        if (userRepository.findStaffByEmail(email).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A staff account with this email already exists");
        }
        if (userRepository.findByUsername(username).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this username already exists");
        }

        Role role = resolveRole(actingAdmin, request.role());

        User user = new User();
        user.setEmail(email);
        user.setUsername(username);
        user.setName(request.name().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(role);
        user.setActive(true);

        User created = userRepository.persist(user);
        log.info(
                "Admin account created email={} username={} role={} by={}",
                created.getEmail(),
                created.getUsername(),
                created.getRole(),
                actingAdmin.getEmail()
        );
        return created;
    }

    @Transactional(readOnly = true)
    public List<User> listAdmins() {
        return userRepository.findByRoleIn(STAFF_ROLES);
    }

    @Transactional
    public User setActive(AuthenticatedUser actingAdmin, Long userId, boolean active) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Admin account not found"));

        if (!STAFF_ROLES.contains(user.getRole())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Not a staff account");
        }
        if (userId.equals(actingAdmin.getId()) && !active) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot deactivate your own account");
        }

        user.setActive(active);
        user.setUpdatedAt(Instant.now());
        User saved = userRepository.save(user);
        log.info("Admin account {} email={} by={}", active ? "activated" : "deactivated", saved.getEmail(), actingAdmin.getEmail());
        return saved;
    }

    private Role resolveRole(AuthenticatedUser actingAdmin, String requestedRole) {
        if (requestedRole == null || requestedRole.isBlank()) {
            return Role.ADMIN;
        }
        Role role;
        try {
            role = Role.valueOf(requestedRole.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown role: " + requestedRole);
        }
        if (role == Role.USER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Staff accounts must be ADMIN, SUPER_ADMIN, or MONITORING");
        }
        if (role == Role.SUPER_ADMIN && actingAdmin.getRole() != Role.SUPER_ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only a Super Admin can create another Super Admin");
        }
        return role;
    }

    private void requireAllowedDomain(String email) {
        String suffix = "@" + authProperties.primaryEmailDomain();
        if (!email.endsWith(suffix)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Admin accounts must use a " + suffix + " email");
        }
    }

    private void requireValidUsername(String username) {
        if (username.contains("@")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username cannot be an email address");
        }
    }
}

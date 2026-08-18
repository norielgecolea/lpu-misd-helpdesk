package org.lpu.dev.codes.helpdesk.service;

import java.time.Instant;
import org.lpu.dev.codes.helpdesk.model.Role;
import org.lpu.dev.codes.helpdesk.model.User;
import org.lpu.dev.codes.helpdesk.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final DirectoryLookupService directoryLookupService;
    private final DirectoryEmailService directoryEmailService;

    public UserService(
            UserRepository userRepository,
            DirectoryLookupService directoryLookupService,
            DirectoryEmailService directoryEmailService
    ) {
        this.userRepository = userRepository;
        this.directoryLookupService = directoryLookupService;
        this.directoryEmailService = directoryEmailService;
    }

    /**
     * Looks up a user by email, creating one on first sign-in. Used by both
     * the Microsoft and OTP login flows so either method works for the same
     * account. Prefer the official name from the gate student/employee record
     * when the LPU email matches.
     */
    @Transactional
    public User findOrCreateByEmail(String email, String fallbackName) {
        String normalizedEmail = email.trim().toLowerCase();
        String directoryName = directoryLookupService.findNameByLpuEmail(normalizedEmail).orElse(null);
        String resolvedName = directoryName != null
                ? directoryName
                : (fallbackName != null && !fallbackName.isBlank() ? fallbackName.trim() : normalizedEmail);

        User user = userRepository.findUserByEmail(normalizedEmail)
                .map(existing -> {
                    if (directoryName != null) {
                        existing.setName(directoryName);
                    } else if (fallbackName != null && !fallbackName.isBlank()
                            && (existing.getName() == null
                            || existing.getName().isBlank()
                            || existing.getName().equalsIgnoreCase(normalizedEmail))) {
                        existing.setName(fallbackName.trim());
                    }
                    existing.setLastLoginAt(Instant.now());
                    existing.setUpdatedAt(Instant.now());
                    return userRepository.save(existing);
                })
                .orElseGet(() -> {
                    User created = new User();
                    created.setEmail(normalizedEmail);
                    created.setName(resolvedName);
                    created.setRole(Role.USER);
                    created.setLastLoginAt(Instant.now());
                    return userRepository.persist(created);
                });
        directoryEmailService.linkTicketsForLpuEmail(user.getEmail(), user.getId());
        return user;
    }
}

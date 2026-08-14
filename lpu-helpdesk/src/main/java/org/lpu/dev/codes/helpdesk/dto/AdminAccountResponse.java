package org.lpu.dev.codes.helpdesk.dto;

import java.time.Instant;
import org.lpu.dev.codes.helpdesk.model.User;

public record AdminAccountResponse(
        Long id,
        String email,
        String username,
        String name,
        String role,
        boolean active,
        Instant createdAt,
        Instant lastLoginAt
) {
    public static AdminAccountResponse from(User user) {
        return new AdminAccountResponse(
                user.getId(),
                user.getEmail(),
                user.getUsername(),
                user.getName(),
                user.getRole().name(),
                user.isActive(),
                user.getCreatedAt(),
                user.getLastLoginAt()
        );
    }
}

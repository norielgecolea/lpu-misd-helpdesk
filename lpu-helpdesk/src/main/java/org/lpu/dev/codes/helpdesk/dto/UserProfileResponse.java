package org.lpu.dev.codes.helpdesk.dto;

import org.lpu.dev.codes.helpdesk.model.User;

public record UserProfileResponse(
        Long id,
        String email,
        String name,
        String role
) {
    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getRole().name()
        );
    }
}

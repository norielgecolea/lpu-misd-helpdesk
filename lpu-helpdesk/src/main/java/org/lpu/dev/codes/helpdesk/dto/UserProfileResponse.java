package org.lpu.dev.codes.helpdesk.dto;

import org.lpu.dev.codes.helpdesk.model.User;

public record UserProfileResponse(
        Long id,
        String email,
        String name,
        String role,
        boolean needsStudentInfo,
        String declaredStudentName,
        String declaredStudentNo,
        String declaredPersonType,
        String declaredLpuEmail
) {
    public static UserProfileResponse from(User user, boolean needsStudentInfo) {
        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getRole().name(),
                needsStudentInfo,
                user.getDeclaredStudentName(),
                user.getDeclaredStudentNo(),
                user.getDeclaredPersonType(),
                user.getDeclaredLpuEmail()
        );
    }
}

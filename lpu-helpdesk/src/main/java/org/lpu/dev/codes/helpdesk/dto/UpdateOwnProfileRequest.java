package org.lpu.dev.codes.helpdesk.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateOwnProfileRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "A valid email address is required")
        String email,

        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 50, message = "Username must be 3–50 characters")
        @Pattern(
                regexp = "^[a-zA-Z0-9._-]+$",
                message = "Username may only contain letters, numbers, dots, underscores, and hyphens"
        )
        String username,

        @NotBlank(message = "Name is required")
        String name,

        /** Required when setting a new password. */
        String currentPassword,

        /** Optional; omitted or blank leaves the current password unchanged. */
        String newPassword
) {
}

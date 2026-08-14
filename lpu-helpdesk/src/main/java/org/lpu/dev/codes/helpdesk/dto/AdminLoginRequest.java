package org.lpu.dev.codes.helpdesk.dto;

import jakarta.validation.constraints.NotBlank;

public record AdminLoginRequest(
        /** Username or email. */
        @NotBlank(message = "Username or email is required")
        String login,

        @NotBlank(message = "Password is required")
        String password,

        Boolean rememberMe
) {
}

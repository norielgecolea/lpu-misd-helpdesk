package org.lpu.dev.codes.helpdesk.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record OtpVerifyRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "A valid email address is required")
        String email,

        @NotBlank(message = "Code is required")
        String code
) {
}

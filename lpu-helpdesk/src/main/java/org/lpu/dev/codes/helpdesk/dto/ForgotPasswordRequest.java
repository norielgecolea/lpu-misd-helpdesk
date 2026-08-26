package org.lpu.dev.codes.helpdesk.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record ForgotPasswordRequest(
        @NotBlank(message = "Username or email is required")
        String login,

        @JsonProperty("cf-turnstile-response")
        String turnstileResponse
) {}

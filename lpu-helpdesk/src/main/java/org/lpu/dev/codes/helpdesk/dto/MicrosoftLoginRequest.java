package org.lpu.dev.codes.helpdesk.dto;

import jakarta.validation.constraints.NotBlank;

public record MicrosoftLoginRequest(
        @NotBlank(message = "idToken is required") String idToken
) {
}

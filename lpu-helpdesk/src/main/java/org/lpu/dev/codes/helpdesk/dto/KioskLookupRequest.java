package org.lpu.dev.codes.helpdesk.dto;

import jakarta.validation.constraints.NotBlank;

public record KioskLookupRequest(
        @NotBlank(message = "RFID or ID number is required")
        String identifier
) {
}

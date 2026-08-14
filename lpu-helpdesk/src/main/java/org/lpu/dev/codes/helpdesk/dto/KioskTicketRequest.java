package org.lpu.dev.codes.helpdesk.dto;

import jakarta.validation.constraints.NotBlank;

public record KioskTicketRequest(
        @NotBlank(message = "RFID or ID number is required")
        String identifier,

        @NotBlank(message = "Category is required")
        String category,

        /** Required when category is OTHERS — free-text concern from the student. */
        String concern
) {
}

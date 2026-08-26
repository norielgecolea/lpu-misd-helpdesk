package org.lpu.dev.codes.helpdesk.dto;

import jakarta.validation.constraints.NotBlank;

public record KioskTicketRequest(
        @NotBlank(message = "RFID or ID number is required")
        String identifier,

        @NotBlank(message = "Category is required")
        String category,

        @NotBlank(message = "Problem is required")
        String subcategory,

        /** Required when the chosen problem has requiresDetail. */
        String concern
) {
}

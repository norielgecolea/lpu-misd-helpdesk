package org.lpu.dev.codes.helpdesk.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record WalkInTicketRequest(
        @NotBlank(message = "Name is required")
        String name,

        @NotBlank(message = "Email is required")
        @Email(message = "A valid email address is required")
        String email,

        @NotBlank(message = "Category is required")
        String category,

        @NotBlank(message = "Subject is required")
        String subject,

        String description,

        /** Optional: STUDENT or EMPLOYEE from RFID kiosk lookup. */
        String personType,

        /** Optional: student number or employee number. */
        String personNo
) {
}

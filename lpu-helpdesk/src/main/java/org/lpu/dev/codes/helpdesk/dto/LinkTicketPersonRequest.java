package org.lpu.dev.codes.helpdesk.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record LinkTicketPersonRequest(
        @NotNull(message = "Ticket id is required")
        Long ticketId,

        /** STUDENT or EMPLOYEE. Optional when the number is unique across both tables. */
        String personType,

        @NotBlank(message = "Student or employee number is required")
        String personNo
) {
}

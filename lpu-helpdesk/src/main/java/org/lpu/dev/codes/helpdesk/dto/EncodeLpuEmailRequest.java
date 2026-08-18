package org.lpu.dev.codes.helpdesk.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record EncodeLpuEmailRequest(
        @NotBlank(message = "LPU email is required")
        @Email(message = "A valid email address is required")
        String email,

        /** STUDENT or EMPLOYEE. Optional when {@code ticketId} is provided. */
        String personType,

        /** Student / employee number. Optional when {@code ticketId} is provided. */
        String personNo,

        /** When set, person identity is taken from this ticket if type/number are omitted. */
        Long ticketId
) {
}

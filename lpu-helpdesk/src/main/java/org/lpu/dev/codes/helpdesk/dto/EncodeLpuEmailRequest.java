package org.lpu.dev.codes.helpdesk.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record EncodeLpuEmailRequest(
        @NotBlank(message = "LPU email is required")
        @Email(message = "A valid email address is required")
        String email,

        /** STUDENT or EMPLOYEE. Optional when the number is unique, or when {@code ticketId} already has identity. */
        String personType,

        /** Student / employee number. Required to link an online email that is not yet on a directory record. */
        String personNo,

        /** When set, missing email/identity fields are taken from this ticket. */
        Long ticketId
) {
}

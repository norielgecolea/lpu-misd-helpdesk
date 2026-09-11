package org.lpu.dev.codes.helpdesk.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record StudentInfoRequest(
        @NotBlank(message = "Person type is required")
        @Size(max = 20, message = "Person type must be at most 20 characters")
        String personType,

        @NotBlank(message = "Name is required")
        @Size(max = 150, message = "Name must be at most 150 characters")
        String studentName,

        @NotBlank(message = "ID number is required")
        @Pattern(
                regexp = "^\\d{4}-\\d+$",
                message = "ID number must be YEAR-NUMBER, e.g. 2020-10184 or 2026-2650"
        )
        @Size(max = 50, message = "ID number must be at most 50 characters")
        String studentNo,

        @NotBlank(message = "LPU email is required")
        @Email(message = "A valid LPU email is required")
        @Size(max = 255, message = "LPU email must be at most 255 characters")
        String lpuEmail
) {
}

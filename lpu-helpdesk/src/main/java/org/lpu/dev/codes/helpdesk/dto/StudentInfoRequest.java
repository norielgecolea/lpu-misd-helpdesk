package org.lpu.dev.codes.helpdesk.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StudentInfoRequest(
        @NotBlank(message = "Student name is required")
        @Size(max = 150, message = "Student name must be at most 150 characters")
        String studentName,

        @NotBlank(message = "Student ID number is required")
        @Size(max = 50, message = "Student ID number must be at most 50 characters")
        String studentNo
) {
}

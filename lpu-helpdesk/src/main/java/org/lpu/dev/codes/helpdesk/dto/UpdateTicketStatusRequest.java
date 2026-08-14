package org.lpu.dev.codes.helpdesk.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateTicketStatusRequest(
        @NotBlank(message = "Status is required")
        String status
) {
}

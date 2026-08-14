package org.lpu.dev.codes.helpdesk.dto;

import jakarta.validation.constraints.NotNull;

/** Transfer an onsite queue ticket to another admin counter. */
public record TransferQueueTicketRequest(
        @NotNull(message = "Target admin is required")
        Long adminId
) {
}

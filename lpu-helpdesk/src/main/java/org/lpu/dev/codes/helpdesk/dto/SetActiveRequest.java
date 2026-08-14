package org.lpu.dev.codes.helpdesk.dto;

import jakarta.validation.constraints.NotNull;

public record SetActiveRequest(
        @NotNull(message = "active is required")
        Boolean active
) {
}

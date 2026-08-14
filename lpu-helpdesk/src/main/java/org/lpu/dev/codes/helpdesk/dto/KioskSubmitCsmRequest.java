package org.lpu.dev.codes.helpdesk.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record KioskSubmitCsmRequest(
        @NotBlank String identifier,
        @NotNull Long ticketId,
        @NotBlank @Size(max = 20) String rating,
        @Size(max = 2000) String comment
) {
}

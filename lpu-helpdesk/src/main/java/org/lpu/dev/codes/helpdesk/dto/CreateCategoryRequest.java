package org.lpu.dev.codes.helpdesk.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCategoryRequest(
        @NotBlank @Size(max = 40) String code,
        @NotBlank @Size(max = 120) String label,
        Integer sortOrder,
        Boolean showOnKiosk,
        Boolean showOnline,
        Boolean requiresDetail
) {
}

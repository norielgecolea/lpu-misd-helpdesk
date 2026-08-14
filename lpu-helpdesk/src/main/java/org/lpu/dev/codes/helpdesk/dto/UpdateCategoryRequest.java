package org.lpu.dev.codes.helpdesk.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateCategoryRequest(
        @NotBlank @Size(max = 120) String label,
        Integer sortOrder,
        Boolean active,
        Boolean showOnKiosk,
        Boolean showOnline,
        Boolean requiresDetail
) {
}

package org.lpu.dev.codes.helpdesk.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateTicketCategoryRequest(
        @NotBlank(message = "Category is required")
        String category,
        @NotBlank(message = "Concern is required")
        String subcategory
) {
}

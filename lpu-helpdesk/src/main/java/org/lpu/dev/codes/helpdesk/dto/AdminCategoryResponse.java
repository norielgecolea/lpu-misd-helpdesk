package org.lpu.dev.codes.helpdesk.dto;

import java.time.Instant;
import org.lpu.dev.codes.helpdesk.model.TicketCategoryDefinition;

public record AdminCategoryResponse(
        Long id,
        String code,
        String label,
        int sortOrder,
        boolean active,
        boolean showOnKiosk,
        boolean showOnline,
        boolean requiresDetail,
        Instant createdAt,
        Instant updatedAt
) {
    public static AdminCategoryResponse from(TicketCategoryDefinition category) {
        return new AdminCategoryResponse(
                category.getId(),
                category.getCode(),
                category.getLabel(),
                category.getSortOrder(),
                category.isActive(),
                category.isShowOnKiosk(),
                category.isShowOnline(),
                category.isRequiresDetail(),
                category.getCreatedAt(),
                category.getUpdatedAt()
        );
    }
}

package org.lpu.dev.codes.helpdesk.dto;

import java.time.Instant;
import java.util.List;
import org.lpu.dev.codes.helpdesk.model.TicketCategoryDefinition;

public record AdminCategoryResponse(
        Long id,
        Long parentId,
        String code,
        String label,
        int sortOrder,
        boolean active,
        boolean showOnKiosk,
        boolean showOnline,
        boolean requiresDetail,
        Instant createdAt,
        Instant updatedAt,
        List<AdminCategoryResponse> children
) {
    public static AdminCategoryResponse from(TicketCategoryDefinition category) {
        return from(category, List.of());
    }

    public static AdminCategoryResponse from(
            TicketCategoryDefinition category,
            List<AdminCategoryResponse> children
    ) {
        return new AdminCategoryResponse(
                category.getId(),
                category.getParentId(),
                category.getCode(),
                category.getLabel(),
                category.getSortOrder(),
                category.isActive(),
                category.isShowOnKiosk(),
                category.isShowOnline(),
                category.isRequiresDetail(),
                category.getCreatedAt(),
                category.getUpdatedAt(),
                children != null ? children : List.of()
        );
    }
}

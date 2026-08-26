package org.lpu.dev.codes.helpdesk.dto;

import java.util.List;

public record TicketCategoryOption(
        String value,
        String label,
        boolean requiresDetail,
        List<TicketCategoryOption> children
) {
    public TicketCategoryOption(String value, String label, boolean requiresDetail) {
        this(value, label, requiresDetail, List.of());
    }

    public TicketCategoryOption(String value, String label) {
        this(value, label, "OTHERS".equalsIgnoreCase(value), List.of());
    }
}

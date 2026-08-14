package org.lpu.dev.codes.helpdesk.dto;

public record TicketCategoryOption(
        String value,
        String label,
        boolean requiresDetail
) {
    public TicketCategoryOption(String value, String label) {
        this(value, label, "OTHERS".equalsIgnoreCase(value));
    }
}

package org.lpu.dev.codes.helpdesk.dto;

public record ServerTimeResponse(
        long epochMillis,
        String iso,
        String timezone
) {
}

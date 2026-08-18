package org.lpu.dev.codes.helpdesk.dto;

public record EncodeLpuEmailResponse(
        String email,
        String personType,
        String personNo,
        int ticketsLinked
) {
}

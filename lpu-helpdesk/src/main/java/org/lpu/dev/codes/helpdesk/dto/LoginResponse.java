package org.lpu.dev.codes.helpdesk.dto;

public record LoginResponse(
        Long id,
        String token,
        String tokenType,
        String email,
        String name,
        String role,
        long expiresInMs
) {
}

package org.lpu.dev.codes.helpdesk.dto;

public record KioskPersonResponse(
        String personType,
        Long id,
        String name,
        String personNo,
        String email,
        String photo,
        String department,
        String course,
        String school,
        String position,
        String rfid
) {
}

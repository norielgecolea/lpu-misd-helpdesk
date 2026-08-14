package org.lpu.dev.codes.helpdesk.dto;

/**
 * Directory profile for an admin ticket summary.
 * Student: name, email, course, department, personNo (student number).
 * Employee: name, personNo (employee number), department.
 */
public record DirectoryProfileResponse(
        boolean found,
        String personType,
        String name,
        String email,
        String personNo,
        String department,
        String course,
        String position
) {
    public static DirectoryProfileResponse notFound() {
        return new DirectoryProfileResponse(false, null, null, null, null, null, null, null);
    }
}

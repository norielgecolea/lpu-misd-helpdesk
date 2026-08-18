package org.lpu.dev.codes.helpdesk.dto;

/** Directory fields needed for ticket summaries — excludes ID photo blobs. */
public record DirectoryIdentity(
        String personType,
        String name,
        String email,
        String personNo,
        String department,
        String course,
        String position
) {
    public DirectoryProfileResponse toProfile() {
        return new DirectoryProfileResponse(
                true,
                personType,
                name,
                email,
                personNo,
                department,
                course,
                position
        );
    }
}

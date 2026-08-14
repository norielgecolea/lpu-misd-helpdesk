package org.lpu.dev.codes.helpdesk.dto;

import java.util.List;

public record StudentPageResponse(List<StudentResponse> items, long total) {
}

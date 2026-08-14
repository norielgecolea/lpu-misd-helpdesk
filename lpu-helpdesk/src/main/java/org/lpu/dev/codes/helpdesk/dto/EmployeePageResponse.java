package org.lpu.dev.codes.helpdesk.dto;

import java.util.List;

public record EmployeePageResponse(List<EmployeeResponse> items, long total) {
}

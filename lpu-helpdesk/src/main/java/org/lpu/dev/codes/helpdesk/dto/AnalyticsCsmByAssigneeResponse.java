package org.lpu.dev.codes.helpdesk.dto;

import java.util.List;

public record AnalyticsCsmByAssigneeResponse(List<AssigneeCsm> byAssignee) {
    public record AssigneeCsm(
            Long adminId,
            String name,
            long sad,
            long neutral,
            long happy,
            long total
    ) {
    }
}

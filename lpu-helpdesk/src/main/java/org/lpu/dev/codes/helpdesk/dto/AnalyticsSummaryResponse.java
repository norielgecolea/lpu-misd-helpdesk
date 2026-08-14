package org.lpu.dev.codes.helpdesk.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record AnalyticsSummaryResponse(
        Instant from,
        Instant to,
        Totals totals,
        List<NamedCount> byStatus,
        List<NamedCount> byChannel,
        List<NamedCount> byCategory,
        List<DayVolume> volumeByDay,
        List<DayCsm> csmByDay,
        List<AssigneeLoad> byAssignee,
        QueueNow queueToday
) {
    public record Totals(
            long created,
            long closed,
            long open,
            long inProgress,
            long unassignedOpen,
            Double avgResolveHours,
            long csmCount,
            Map<String, Long> csmByRating,
            Double csmHappyPercent
    ) {
    }

    public record NamedCount(String key, String label, long count) {
    }

    public record DayVolume(String date, long created, long closed) {
    }

    public record DayCsm(String date, long sad, long neutral, long happy) {
    }

    public record AssigneeLoad(
            Long adminId,
            String name,
            long open,
            long inProgress,
            long closed
    ) {
    }

    public record QueueNow(long waiting, long serving) {
    }
}

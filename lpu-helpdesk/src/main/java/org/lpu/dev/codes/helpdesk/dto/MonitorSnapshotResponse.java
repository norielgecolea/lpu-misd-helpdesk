package org.lpu.dev.codes.helpdesk.dto;

import java.util.List;

public record MonitorSnapshotResponse(
        List<QueueSnapshotResponse.NowServingEntry> nowServing,
        List<TicketResponse> waiting,
        List<TicketResponse> recentTickets
) {
}

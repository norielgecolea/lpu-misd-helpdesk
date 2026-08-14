package org.lpu.dev.codes.helpdesk.dto;

import java.util.List;

public record QueueSnapshotResponse(
        List<TicketResponse> waiting,
        List<NowServingEntry> nowServing,
        List<QueueTransferResponse> pendingTransfers
) {
    public record NowServingEntry(Long adminId, String adminName, TicketResponse ticket) {
    }
}

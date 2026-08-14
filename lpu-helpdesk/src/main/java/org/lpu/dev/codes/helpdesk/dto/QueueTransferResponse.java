package org.lpu.dev.codes.helpdesk.dto;

import java.time.Instant;

public record QueueTransferResponse(
        Long id,
        Long ticketId,
        String ticketNumber,
        Integer queueNumber,
        String requesterName,
        String requesterPersonNo,
        String categoryLabel,
        Long fromAdminId,
        String fromAdminName,
        Long toAdminId,
        String toAdminName,
        String status,
        Instant createdAt
) {
}

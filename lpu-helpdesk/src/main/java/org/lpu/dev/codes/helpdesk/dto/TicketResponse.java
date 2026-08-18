package org.lpu.dev.codes.helpdesk.dto;

import java.time.Instant;
import org.lpu.dev.codes.helpdesk.model.PendingRequesterEmail;
import org.lpu.dev.codes.helpdesk.model.Ticket;
import org.lpu.dev.codes.helpdesk.service.CategoryLabelCache;

public record TicketResponse(
        Long id,
        String ticketNumber,
        String requesterEmail,
        String requesterName,
        String requesterPersonType,
        String requesterPersonNo,
        String category,
        String categoryLabel,
        String subject,
        String description,
        String status,
        String channel,
        Long assignedAdminId,
        String assignedAdminName,
        Integer queueNumber,
        boolean hasIdPhoto,
        int unreadCount,
        boolean pendingEmail,
        boolean directoryUnlinked,
        Instant createdAt,
        Instant updatedAt,
        Instant resolvedAt
) {
    public static TicketResponse from(Ticket ticket) {
        return from(ticket, null, 0);
    }

    public static TicketResponse from(Ticket ticket, String assignedAdminName) {
        return from(ticket, assignedAdminName, 0);
    }

    public static TicketResponse from(Ticket ticket, String assignedAdminName, int unreadCount) {
        boolean hasId = ticket.getIdPhotoFilename() != null && !ticket.getIdPhotoFilename().isBlank();
        boolean pendingEmail = PendingRequesterEmail.isPending(ticket.getRequesterEmail());
        return new TicketResponse(
                ticket.getId(),
                ticket.getTicketNumber(),
                ticket.getRequesterEmail(),
                ticket.getRequesterName(),
                ticket.getRequesterPersonType(),
                ticket.getRequesterPersonNo(),
                ticket.getCategory(),
                CategoryLabelCache.labelFor(ticket.getCategory()),
                ticket.getSubject(),
                ticket.getDescription(),
                ticket.getStatus().name(),
                ticket.getChannel().name(),
                ticket.getAssignedAdminId(),
                assignedAdminName,
                ticket.getQueueNumber(),
                hasId,
                Math.max(0, unreadCount),
                pendingEmail,
                isDirectoryUnlinked(ticket, pendingEmail),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt(),
                ticket.getResolvedAt()
        );
    }

    /** Real LPU email on the ticket, but no student/employee number attached yet. */
    private static boolean isDirectoryUnlinked(Ticket ticket, boolean pendingEmail) {
        if (pendingEmail) {
            return false;
        }
        String type = ticket.getRequesterPersonType();
        String number = ticket.getRequesterPersonNo();
        return type == null || type.isBlank() || number == null || number.isBlank();
    }
}

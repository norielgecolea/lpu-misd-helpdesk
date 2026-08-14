package org.lpu.dev.codes.helpdesk.service;

import java.util.List;
import java.util.Map;
import org.lpu.dev.codes.helpdesk.dto.MonitorSnapshotResponse;
import org.lpu.dev.codes.helpdesk.dto.QueueSnapshotResponse;
import org.lpu.dev.codes.helpdesk.dto.TicketResponse;
import org.lpu.dev.codes.helpdesk.model.Ticket;
import org.lpu.dev.codes.helpdesk.model.User;
import org.lpu.dev.codes.helpdesk.repository.TicketRepository;
import org.lpu.dev.codes.helpdesk.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MonitorService {

    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;

    public MonitorService(TicketRepository ticketRepository, UserRepository userRepository) {
        this.ticketRepository = ticketRepository;
        this.userRepository = userRepository;
    }

    /**
     * Lean snapshot for the TV board — waiting + now-serving + recent online only.
     * Avoids transfer-request work used by the admin queue page.
     */
    @Transactional(readOnly = true)
    public MonitorSnapshotResponse getSnapshot(int recentLimit) {
        List<Ticket> waiting = ticketRepository.findWaitingOnsiteOrderByQueueNumber();
        List<Ticket> serving = ticketRepository.findServingOnsite();
        List<Ticket> recent = ticketRepository.findRecentOnlineOrderByCreatedAtDesc(recentLimit);

        Map<Long, User> adminsById = userRepository.findByIdIn(
                serving.stream().map(Ticket::getAssignedAdminId).distinct().toList()
        );

        List<QueueSnapshotResponse.NowServingEntry> nowServing = serving.stream()
                .map(t -> {
                    User admin = adminsById.get(t.getAssignedAdminId());
                    String adminName = admin != null ? admin.getName() : "Unknown";
                    return new QueueSnapshotResponse.NowServingEntry(
                            t.getAssignedAdminId(),
                            adminName,
                            TicketResponse.from(t, adminName)
                    );
                })
                .toList();

        List<TicketResponse> waitingResponses = waiting.stream().map(TicketResponse::from).toList();
        List<TicketResponse> recentResponses = recent.stream().map(TicketResponse::from).toList();

        return new MonitorSnapshotResponse(nowServing, waitingResponses, recentResponses);
    }
}

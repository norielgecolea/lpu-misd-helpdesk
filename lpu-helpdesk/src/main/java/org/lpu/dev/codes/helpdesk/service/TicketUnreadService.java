package org.lpu.dev.codes.helpdesk.service;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.lpu.dev.codes.helpdesk.repository.TicketMessageReadRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TicketUnreadService {

    private final TicketMessageReadRepository ticketMessageReadRepository;

    public TicketUnreadService(TicketMessageReadRepository ticketMessageReadRepository) {
        this.ticketMessageReadRepository = ticketMessageReadRepository;
    }

    @Transactional(readOnly = true)
    public Map<Long, Integer> unreadCounts(Long userId, Collection<Long> ticketIds) {
        if (userId == null || ticketIds == null || ticketIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return ticketMessageReadRepository.countUnreadByTicketIds(userId, ticketIds);
    }

    @Transactional
    public void markReadUpTo(Long userId, Long ticketId, Long lastMessageId) {
        ticketMessageReadRepository.markRead(userId, ticketId, lastMessageId);
    }

    @Transactional
    public void markReadUpToLatest(Long userId, Long ticketId, List<Long> messageIdsNewestLast) {
        if (messageIdsNewestLast == null || messageIdsNewestLast.isEmpty()) {
            return;
        }
        Long latest = messageIdsNewestLast.get(messageIdsNewestLast.size() - 1);
        markReadUpTo(userId, ticketId, latest);
    }
}

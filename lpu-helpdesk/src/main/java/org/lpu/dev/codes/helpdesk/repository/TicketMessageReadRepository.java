package org.lpu.dev.codes.helpdesk.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.lpu.dev.codes.helpdesk.model.TicketMessageRead;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class TicketMessageReadRepository {

    private final SessionFactory sessionFactory;

    public TicketMessageReadRepository(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    private Session currentSession() {
        return sessionFactory.getCurrentSession();
    }

    @Transactional(readOnly = true)
    public Optional<TicketMessageRead> find(Long userId, Long ticketId) {
        return currentSession()
                .createQuery(
                        "FROM TicketMessageRead r WHERE r.userId = :userId AND r.ticketId = :ticketId",
                        TicketMessageRead.class
                )
                .setParameter("userId", userId)
                .setParameter("ticketId", ticketId)
                .uniqueResultOptional();
    }

    /**
     * Unread = messages on the ticket after the user's last-read cursor whose
     * author is not the current user.
     */
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public Map<Long, Integer> countUnreadByTicketIds(Long userId, Collection<Long> ticketIds) {
        if (ticketIds == null || ticketIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Object[]> rows = currentSession()
                .createNativeQuery("""
                        SELECT m.ticket_id, COUNT(*)::int
                        FROM ticket_messages m
                        LEFT JOIN ticket_message_reads r
                            ON r.ticket_id = m.ticket_id AND r.user_id = :userId
                        WHERE m.ticket_id IN (:ticketIds)
                          AND m.id > COALESCE(r.last_read_message_id, 0)
                          AND (m.author_user_id IS NULL OR m.author_user_id <> :userId)
                        GROUP BY m.ticket_id
                        """)
                .setParameter("userId", userId)
                .setParameter("ticketIds", ticketIds)
                .getResultList();

        Map<Long, Integer> result = new HashMap<>();
        for (Object[] row : rows) {
            result.put(((Number) row[0]).longValue(), ((Number) row[1]).intValue());
        }
        return result;
    }

    @Transactional
    public void markRead(Long userId, Long ticketId, Long lastReadMessageId) {
        if (userId == null || ticketId == null || lastReadMessageId == null || lastReadMessageId <= 0) {
            return;
        }
        TicketMessageRead existing = find(userId, ticketId).orElse(null);
        if (existing == null) {
            TicketMessageRead created = new TicketMessageRead();
            created.setUserId(userId);
            created.setTicketId(ticketId);
            created.setLastReadMessageId(lastReadMessageId);
            created.setUpdatedAt(Instant.now());
            currentSession().persist(created);
            return;
        }
        if (lastReadMessageId > existing.getLastReadMessageId()) {
            existing.setLastReadMessageId(lastReadMessageId);
            existing.setUpdatedAt(Instant.now());
            currentSession().merge(existing);
        }
    }
}

package org.lpu.dev.codes.helpdesk.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.lpu.dev.codes.helpdesk.model.CsmRating;
import org.lpu.dev.codes.helpdesk.model.TicketCsm;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class TicketCsmRepository {

    private final SessionFactory sessionFactory;

    public TicketCsmRepository(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    private Session currentSession() {
        return sessionFactory.getCurrentSession();
    }

    @Transactional(readOnly = true)
    public Optional<TicketCsm> findByTicketId(Long ticketId) {
        return currentSession()
                .createQuery("FROM TicketCsm c WHERE c.ticketId = :ticketId", TicketCsm.class)
                .setParameter("ticketId", ticketId)
                .uniqueResultOptional();
    }

    @Transactional(readOnly = true)
    public boolean existsByTicketId(Long ticketId) {
        Long count = currentSession()
                .createQuery("SELECT count(c.id) FROM TicketCsm c WHERE c.ticketId = :ticketId", Long.class)
                .setParameter("ticketId", ticketId)
                .uniqueResult();
        return count != null && count > 0;
    }

    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<Object[]> countByRatingBetween(Instant from, Instant to) {
        return currentSession()
                .createQuery(
                        "SELECT c.rating, count(c.id) FROM TicketCsm c "
                                + "WHERE c.submittedAt >= :from AND c.submittedAt < :to GROUP BY c.rating",
                        Object[].class
                )
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList();
    }

    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<Object[]> countByDayAndRating(Instant from, Instant to) {
        return currentSession()
                .createNativeQuery(
                        """
                        SELECT to_char(submitted_at AT TIME ZONE 'UTC', 'YYYY-MM-DD') AS day,
                               rating,
                               COUNT(*)
                        FROM ticket_csm
                        WHERE submitted_at >= :from AND submitted_at < :to
                        GROUP BY day, rating
                        ORDER BY day
                        """
                )
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList();
    }

    /** CSM rows for a rating in range, newest first. Optionally limited to one assignee. */
    @Transactional(readOnly = true)
    public List<TicketCsm> findByRatingBetween(
            CsmRating rating,
            Instant from,
            Instant to,
            Long assignedAdminId,
            int limit
    ) {
        if (assignedAdminId == null) {
            return currentSession()
                    .createQuery(
                            "FROM TicketCsm c WHERE c.rating = :rating "
                                    + "AND c.submittedAt >= :from AND c.submittedAt < :to "
                                    + "ORDER BY c.submittedAt DESC",
                            TicketCsm.class
                    )
                    .setParameter("rating", rating)
                    .setParameter("from", from)
                    .setParameter("to", to)
                    .setMaxResults(Math.max(1, limit))
                    .getResultList();
        }
        return currentSession()
                .createQuery(
                        "SELECT c FROM TicketCsm c, Ticket t WHERE c.ticketId = t.id "
                                + "AND c.rating = :rating "
                                + "AND c.submittedAt >= :from AND c.submittedAt < :to "
                                + "AND t.assignedAdminId = :adminId "
                                + "ORDER BY c.submittedAt DESC",
                        TicketCsm.class
                )
                .setParameter("rating", rating)
                .setParameter("from", from)
                .setParameter("to", to)
                .setParameter("adminId", assignedAdminId)
                .setMaxResults(Math.max(1, limit))
                .getResultList();
    }

    /**
     * Rows: adminId, rating, count — CSM submitted in range grouped by current assignee.
     */
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<Object[]> countByAssigneeAndRatingBetween(Instant from, Instant to) {
        return currentSession()
                .createQuery(
                        "SELECT t.assignedAdminId, c.rating, count(c.id) FROM TicketCsm c, Ticket t "
                                + "WHERE c.ticketId = t.id AND t.assignedAdminId IS NOT NULL "
                                + "AND c.submittedAt >= :from AND c.submittedAt < :to "
                                + "GROUP BY t.assignedAdminId, c.rating",
                        Object[].class
                )
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList();
    }

    @Transactional
    public TicketCsm persist(TicketCsm csm) {
        Session session = currentSession();
        session.persist(csm);
        session.flush();
        return csm;
    }
}

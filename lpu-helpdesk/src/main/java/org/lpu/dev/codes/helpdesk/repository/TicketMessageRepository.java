package org.lpu.dev.codes.helpdesk.repository;

import java.util.List;
import java.util.Optional;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.lpu.dev.codes.helpdesk.model.TicketMessage;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class TicketMessageRepository {

    private final SessionFactory sessionFactory;

    public TicketMessageRepository(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    private Session currentSession() {
        return sessionFactory.getCurrentSession();
    }

    @Transactional(readOnly = true)
    public Optional<TicketMessage> findById(Long id) {
        return Optional.ofNullable(currentSession().get(TicketMessage.class, id));
    }

    @Transactional(readOnly = true)
    public List<TicketMessage> findByTicketIdOrderByCreatedAtAsc(Long ticketId) {
        return currentSession()
                .createQuery(
                        "FROM TicketMessage m WHERE m.ticketId = :ticketId ORDER BY m.createdAt ASC, m.id ASC",
                        TicketMessage.class
                )
                .setParameter("ticketId", ticketId)
                .getResultList();
    }

    @Transactional
    public TicketMessage persist(TicketMessage message) {
        Session session = currentSession();
        session.persist(message);
        session.flush();
        return message;
    }

    @Transactional
    public TicketMessage save(TicketMessage message) {
        return currentSession().merge(message);
    }
}

package org.lpu.dev.codes.helpdesk.repository;

import java.util.List;
import java.util.Optional;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.lpu.dev.codes.helpdesk.model.QueueTransferRequest;
import org.lpu.dev.codes.helpdesk.model.QueueTransferStatus;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class QueueTransferRequestRepository {

    private final SessionFactory sessionFactory;

    public QueueTransferRequestRepository(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    private Session currentSession() {
        return sessionFactory.getCurrentSession();
    }

    @Transactional(readOnly = true)
    public Optional<QueueTransferRequest> findById(Long id) {
        return currentSession()
                .createQuery("FROM QueueTransferRequest r WHERE r.id = :id", QueueTransferRequest.class)
                .setParameter("id", id)
                .uniqueResultOptional();
    }

    @Transactional(readOnly = true)
    public List<QueueTransferRequest> findPending() {
        return currentSession()
                .createQuery(
                        "FROM QueueTransferRequest r WHERE r.status = :status ORDER BY r.createdAt ASC",
                        QueueTransferRequest.class
                )
                .setParameter("status", QueueTransferStatus.PENDING)
                .getResultList();
    }

    @Transactional(readOnly = true)
    public boolean existsPendingForTicket(Long ticketId) {
        Long count = currentSession()
                .createQuery(
                        "SELECT COUNT(r) FROM QueueTransferRequest r WHERE r.ticketId = :ticketId AND r.status = :status",
                        Long.class
                )
                .setParameter("ticketId", ticketId)
                .setParameter("status", QueueTransferStatus.PENDING)
                .uniqueResult();
        return count != null && count > 0;
    }

    @Transactional
    public QueueTransferRequest persist(QueueTransferRequest request) {
        Session session = currentSession();
        session.persist(request);
        session.flush();
        return request;
    }

    @Transactional
    public QueueTransferRequest save(QueueTransferRequest request) {
        return currentSession().merge(request);
    }
}

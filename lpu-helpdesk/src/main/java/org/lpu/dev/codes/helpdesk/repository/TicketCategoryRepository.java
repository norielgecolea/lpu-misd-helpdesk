package org.lpu.dev.codes.helpdesk.repository;

import java.util.List;
import java.util.Optional;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.lpu.dev.codes.helpdesk.model.TicketCategoryDefinition;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class TicketCategoryRepository {

    private final SessionFactory sessionFactory;

    public TicketCategoryRepository(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    private Session currentSession() {
        return sessionFactory.getCurrentSession();
    }

    @Transactional(readOnly = true)
    public List<TicketCategoryDefinition> findAllOrdered() {
        return currentSession()
                .createQuery(
                        "FROM TicketCategoryDefinition c ORDER BY c.sortOrder ASC, c.label ASC, c.id ASC",
                        TicketCategoryDefinition.class
                )
                .getResultList();
    }

    @Transactional(readOnly = true)
    public List<TicketCategoryDefinition> findActiveForKiosk() {
        return currentSession()
                .createQuery(
                        "FROM TicketCategoryDefinition c WHERE c.active = true AND c.showOnKiosk = true"
                                + " ORDER BY c.sortOrder ASC, c.label ASC, c.id ASC",
                        TicketCategoryDefinition.class
                )
                .getResultList();
    }

    @Transactional(readOnly = true)
    public List<TicketCategoryDefinition> findActiveForOnline() {
        return currentSession()
                .createQuery(
                        "FROM TicketCategoryDefinition c WHERE c.active = true AND c.showOnline = true"
                                + " ORDER BY c.sortOrder ASC, c.label ASC, c.id ASC",
                        TicketCategoryDefinition.class
                )
                .getResultList();
    }

    @Transactional(readOnly = true)
    public Optional<TicketCategoryDefinition> findById(Long id) {
        return currentSession()
                .createQuery("FROM TicketCategoryDefinition c WHERE c.id = :id", TicketCategoryDefinition.class)
                .setParameter("id", id)
                .uniqueResultOptional();
    }

    @Transactional(readOnly = true)
    public Optional<TicketCategoryDefinition> findByCode(String code) {
        return currentSession()
                .createQuery("FROM TicketCategoryDefinition c WHERE c.code = :code", TicketCategoryDefinition.class)
                .setParameter("code", code)
                .uniqueResultOptional();
    }

    @Transactional(readOnly = true)
    public boolean existsByCode(String code) {
        Long count = currentSession()
                .createQuery("SELECT COUNT(c) FROM TicketCategoryDefinition c WHERE c.code = :code", Long.class)
                .setParameter("code", code)
                .uniqueResult();
        return count != null && count > 0;
    }

    @Transactional
    public TicketCategoryDefinition persist(TicketCategoryDefinition category) {
        Session session = currentSession();
        session.persist(category);
        session.flush();
        return category;
    }

    @Transactional
    public TicketCategoryDefinition save(TicketCategoryDefinition category) {
        return currentSession().merge(category);
    }
}

package org.lpu.dev.codes.helpdesk.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.lpu.dev.codes.helpdesk.model.Employee;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class EmployeeRepository {

    private final SessionFactory gateSessionFactory;

    public EmployeeRepository(@Qualifier("gateSessionFactory") SessionFactory gateSessionFactory) {
        this.gateSessionFactory = gateSessionFactory;
    }

    private Session currentSession() {
        return gateSessionFactory.getCurrentSession();
    }

    private static final String ACTIVE_SEARCH_WHERE =
            " WHERE e.deleted = false AND (:term = '' OR lower(e.name) LIKE :term"
                    + " OR lower(e.employeeNo) LIKE :term OR lower(coalesce(e.rfid, '')) LIKE :term"
                    + " OR lower(coalesce(e.department, '')) LIKE :term"
                    + " OR lower(coalesce(e.position, '')) LIKE :term)";

    @Transactional(transactionManager = "gateTransactionManager", readOnly = true)
    public List<Employee> searchActive(String term, int offset, int limit) {
        return currentSession()
                .createQuery("FROM Employee e" + ACTIVE_SEARCH_WHERE + " ORDER BY e.name ASC", Employee.class)
                .setParameter("term", likeTerm(term))
                .setFirstResult(offset)
                .setMaxResults(limit)
                .getResultList();
    }

    @Transactional(transactionManager = "gateTransactionManager", readOnly = true)
    public long countActive(String term) {
        Long count = currentSession()
                .createQuery("SELECT COUNT(e.id) FROM Employee e" + ACTIVE_SEARCH_WHERE, Long.class)
                .setParameter("term", likeTerm(term))
                .uniqueResult();
        return count != null ? count : 0;
    }

    @Transactional(transactionManager = "gateTransactionManager")
    public int updateLpuEmail(Long id, String email) {
        return currentSession()
                .createMutationQuery(
                        "UPDATE Employee e SET e.lpuEmail = :email, e.updatedAt = :now "
                                + "WHERE e.id = :id AND e.deleted = false"
                )
                .setParameter("email", email)
                .setParameter("now", java.time.Instant.now())
                .setParameter("id", id)
                .executeUpdate();
    }

    @Transactional(transactionManager = "gateTransactionManager", readOnly = true)
    public Optional<Employee> findById(Long id) {
        return currentSession()
                .createQuery("FROM Employee e WHERE e.id = :id AND e.deleted = false", Employee.class)
                .setParameter("id", id)
                .uniqueResultOptional();
    }

    private static String likeTerm(String term) {
        if (term == null || term.isBlank()) {
            return "";
        }
        return "%" + term.trim().toLowerCase() + "%";
    }

    @Transactional(transactionManager = "gateTransactionManager", readOnly = true)
    public Optional<Employee> findByRfidOrEmployeeNo(String identifier) {
        return currentSession()
                .createQuery(
                        "FROM Employee e WHERE e.deleted = false "
                                + "AND (e.rfid = :identifier OR e.employeeNo = :identifier)",
                        Employee.class
                )
                .setParameter("identifier", identifier)
                .uniqueResultOptional();
    }

    @Transactional(transactionManager = "gateTransactionManager", readOnly = true)
    public Optional<Employee> findByLpuEmail(String email) {
        return currentSession()
                .createQuery(
                        "FROM Employee e WHERE e.deleted = false AND lower(e.lpuEmail) = lower(:email)",
                        Employee.class
                )
                .setParameter("email", email)
                .uniqueResultOptional();
    }

    @Transactional(transactionManager = "gateTransactionManager", readOnly = true)
    public List<Employee> findByLpuEmails(Collection<String> emails) {
        List<String> normalized = normalizeEmails(emails);
        if (normalized.isEmpty()) {
            return List.of();
        }
        return currentSession()
                .createQuery(
                        "FROM Employee e WHERE e.deleted = false AND lower(e.lpuEmail) IN :emails",
                        Employee.class
                )
                .setParameter("emails", normalized)
                .getResultList();
    }

    private static List<String> normalizeEmails(Collection<String> emails) {
        if (emails == null || emails.isEmpty()) {
            return List.of();
        }
        return emails.stream()
                .filter(email -> email != null && !email.isBlank())
                .map(email -> email.trim().toLowerCase())
                .distinct()
                .toList();
    }
}

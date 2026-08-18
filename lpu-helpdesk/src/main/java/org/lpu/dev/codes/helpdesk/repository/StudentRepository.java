package org.lpu.dev.codes.helpdesk.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.lpu.dev.codes.helpdesk.model.Student;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class StudentRepository {

    private final SessionFactory gateSessionFactory;

    public StudentRepository(@Qualifier("gateSessionFactory") SessionFactory gateSessionFactory) {
        this.gateSessionFactory = gateSessionFactory;
    }

    private Session currentSession() {
        return gateSessionFactory.getCurrentSession();
    }

    private static final String ACTIVE_SEARCH_WHERE =
            " WHERE s.deleted = false AND (:term = '' OR lower(s.name) LIKE :term"
                    + " OR lower(s.studentNo) LIKE :term OR lower(coalesce(s.rfid, '')) LIKE :term"
                    + " OR lower(s.department) LIKE :term OR lower(s.course) LIKE :term"
                    + " OR lower(s.school) LIKE :term)";

    @Transactional(transactionManager = "gateTransactionManager", readOnly = true)
    public List<Student> searchActive(String term, int offset, int limit) {
        return currentSession()
                .createQuery("FROM Student s" + ACTIVE_SEARCH_WHERE + " ORDER BY s.name ASC", Student.class)
                .setParameter("term", likeTerm(term))
                .setFirstResult(offset)
                .setMaxResults(limit)
                .getResultList();
    }

    @Transactional(transactionManager = "gateTransactionManager", readOnly = true)
    public long countActive(String term) {
        Long count = currentSession()
                .createQuery("SELECT COUNT(s.id) FROM Student s" + ACTIVE_SEARCH_WHERE, Long.class)
                .setParameter("term", likeTerm(term))
                .uniqueResult();
        return count != null ? count : 0;
    }

    @Transactional(transactionManager = "gateTransactionManager")
    public int updateLpuEmail(Long id, String email) {
        return currentSession()
                .createMutationQuery(
                        "UPDATE Student s SET s.lpuEmail = :email, s.updatedAt = :now "
                                + "WHERE s.id = :id AND s.deleted = false"
                )
                .setParameter("email", email)
                .setParameter("now", java.time.Instant.now())
                .setParameter("id", id)
                .executeUpdate();
    }

    @Transactional(transactionManager = "gateTransactionManager", readOnly = true)
    public Optional<Student> findById(Long id) {
        return currentSession()
                .createQuery("FROM Student s WHERE s.id = :id AND s.deleted = false", Student.class)
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
    public Optional<Student> findByRfidOrStudentNo(String identifier) {
        return currentSession()
                .createQuery(
                        "FROM Student s WHERE s.deleted = false "
                                + "AND (s.rfid = :identifier OR s.studentNo = :identifier)",
                        Student.class
                )
                .setParameter("identifier", identifier)
                .uniqueResultOptional();
    }

    @Transactional(transactionManager = "gateTransactionManager", readOnly = true)
    public Optional<Student> findByLpuEmail(String email) {
        return currentSession()
                .createQuery(
                        "FROM Student s WHERE s.deleted = false AND lower(s.lpuEmail) = lower(:email)",
                        Student.class
                )
                .setParameter("email", email)
                .uniqueResultOptional();
    }

    @Transactional(transactionManager = "gateTransactionManager", readOnly = true)
    public List<Student> findByLpuEmails(Collection<String> emails) {
        List<String> normalized = normalizeEmails(emails);
        if (normalized.isEmpty()) {
            return List.of();
        }
        return currentSession()
                .createQuery(
                        "FROM Student s WHERE s.deleted = false AND lower(s.lpuEmail) IN :emails",
                        Student.class
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

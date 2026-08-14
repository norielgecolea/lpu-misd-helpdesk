package org.lpu.dev.codes.helpdesk.repository;

import java.time.Instant;
import java.util.List;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.lpu.dev.codes.helpdesk.model.PasswordResetToken;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class PasswordResetTokenRepository {

    private final SessionFactory sessionFactory;

    public PasswordResetTokenRepository(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    private Session currentSession() {
        return sessionFactory.getCurrentSession();
    }

    @Transactional
    public PasswordResetToken persist(PasswordResetToken token) {
        Session session = currentSession();
        session.persist(token);
        session.flush();
        return token;
    }

    @Transactional
    public PasswordResetToken save(PasswordResetToken token) {
        return currentSession().merge(token);
    }

    @Transactional
    public void invalidateActiveByUserId(Long userId) {
        currentSession()
                .createMutationQuery(
                        "UPDATE PasswordResetToken t SET t.consumed = true "
                                + "WHERE t.userId = :userId AND t.consumed = false"
                )
                .setParameter("userId", userId)
                .executeUpdate();
    }

    @Transactional(readOnly = true)
    public List<PasswordResetToken> findActiveByUserId(Long userId, Instant now) {
        return currentSession()
                .createQuery(
                        """
                        FROM PasswordResetToken t
                        WHERE t.userId = :userId
                          AND t.consumed = false
                          AND t.expiresAt > :now
                        ORDER BY t.createdAt DESC
                        """,
                        PasswordResetToken.class
                )
                .setParameter("userId", userId)
                .setParameter("now", now)
                .getResultList();
    }

    @Transactional(readOnly = true)
    public List<PasswordResetToken> findUnconsumedNotExpired(Instant now) {
        return currentSession()
                .createQuery(
                        """
                        FROM PasswordResetToken t
                        WHERE t.consumed = false
                          AND t.expiresAt > :now
                        ORDER BY t.createdAt DESC
                        """,
                        PasswordResetToken.class
                )
                .setParameter("now", now)
                .setMaxResults(200)
                .getResultList();
    }
}

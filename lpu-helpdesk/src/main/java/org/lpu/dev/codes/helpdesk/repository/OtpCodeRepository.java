package org.lpu.dev.codes.helpdesk.repository;

import java.time.Instant;
import java.util.Optional;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.lpu.dev.codes.helpdesk.model.OtpCode;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class OtpCodeRepository {

    private final SessionFactory sessionFactory;

    public OtpCodeRepository(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    private Session currentSession() {
        return sessionFactory.getCurrentSession();
    }

    /** Most recent, still-usable (not consumed, not expired) code for an email. */
    @Transactional(readOnly = true)
    public Optional<OtpCode> findLatestActiveByEmail(String email, Instant now) {
        return currentSession()
                .createQuery(
                        "FROM OtpCode o WHERE lower(o.email) = lower(:email)"
                                + " AND o.consumed = false AND o.expiresAt > :now"
                                + " ORDER BY o.createdAt DESC",
                        OtpCode.class
                )
                .setParameter("email", email)
                .setParameter("now", now)
                .setMaxResults(1)
                .uniqueResultOptional();
    }

    /** Invalidates any outstanding codes so only the newest one is redeemable. */
    @Transactional
    public void invalidateActiveByEmail(String email) {
        currentSession()
                .createMutationQuery(
                        "UPDATE OtpCode o SET o.consumed = true"
                                + " WHERE lower(o.email) = lower(:email) AND o.consumed = false")
                .setParameter("email", email)
                .executeUpdate();
    }

    @Transactional
    public OtpCode persist(OtpCode otpCode) {
        Session session = currentSession();
        session.persist(otpCode);
        session.flush();
        return otpCode;
    }

    @Transactional
    public OtpCode save(OtpCode otpCode) {
        return currentSession().merge(otpCode);
    }
}

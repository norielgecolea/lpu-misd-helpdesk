package org.lpu.dev.codes.helpdesk.repository;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.lpu.dev.codes.helpdesk.model.QueueCounter;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class QueueCounterRepository {

    private final SessionFactory sessionFactory;

    public QueueCounterRepository(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    private Session currentSession() {
        return sessionFactory.getCurrentSession();
    }

    /**
     * Atomically hands out the next queue number for today. Takes a
     * pessimistic write lock on the day's counter row so concurrent
     * walk-ins/kiosk taps never receive the same number.
     */
    @Transactional
    public int nextNumberForToday() {
        LocalDate today = LocalDate.now();
        Session session = currentSession();

        QueueCounter counter = session.find(QueueCounter.class, today, LockModeType.PESSIMISTIC_WRITE);
        if (counter == null) {
            counter = new QueueCounter();
            counter.setQueueDate(today);
            counter.setCurrentNumber(0);
            session.persist(counter);
            session.flush();
            counter = session.find(QueueCounter.class, today, LockModeType.PESSIMISTIC_WRITE);
        }

        int next = counter.getCurrentNumber() + 1;
        counter.setCurrentNumber(next);
        session.flush();
        return next;
    }
}

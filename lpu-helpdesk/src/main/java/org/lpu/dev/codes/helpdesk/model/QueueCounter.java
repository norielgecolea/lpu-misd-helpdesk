package org.lpu.dev.codes.helpdesk.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

/** One row per day; {@link #currentNumber} is incremented atomically to hand out queue numbers. */
@Entity
@Table(name = "queue_counters")
public class QueueCounter {

    @Id
    @Column(name = "queue_date", nullable = false)
    private LocalDate queueDate;

    @Column(name = "current_number", nullable = false)
    private int currentNumber = 0;

    public LocalDate getQueueDate() {
        return queueDate;
    }

    public void setQueueDate(LocalDate queueDate) {
        this.queueDate = queueDate;
    }

    public int getCurrentNumber() {
        return currentNumber;
    }

    public void setCurrentNumber(int currentNumber) {
        this.currentNumber = currentNumber;
    }
}

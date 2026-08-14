CREATE TABLE IF NOT EXISTS queue_counters (
    queue_date      DATE PRIMARY KEY,
    current_number  INT NOT NULL DEFAULT 0
);

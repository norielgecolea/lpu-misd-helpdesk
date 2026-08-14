-- Optional bootstrap companion to SchemaMigrationConfig (ticket message read cursors).
CREATE TABLE IF NOT EXISTS ticket_message_reads (
    user_id                BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    ticket_id              BIGINT NOT NULL REFERENCES tickets(id) ON DELETE CASCADE,
    last_read_message_id   BIGINT NOT NULL DEFAULT 0,
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, ticket_id)
);

CREATE TABLE IF NOT EXISTS ticket_messages (
    id                BIGSERIAL PRIMARY KEY,
    ticket_id         BIGINT NOT NULL REFERENCES tickets(id) ON DELETE CASCADE,
    author_user_id    BIGINT REFERENCES users(id),
    author_email      VARCHAR(255) NOT NULL,
    author_name       VARCHAR(150) NOT NULL,
    author_role       VARCHAR(20)  NOT NULL,
    body              TEXT NOT NULL,
    email_message_id  VARCHAR(255),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ticket_messages_ticket_created
    ON ticket_messages (ticket_id, created_at ASC, id ASC);

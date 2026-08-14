CREATE TABLE IF NOT EXISTS tickets (
    id                 BIGSERIAL PRIMARY KEY,
    ticket_number      VARCHAR(30)  UNIQUE,
    requester_user_id  BIGINT REFERENCES users(id),
    requester_email    VARCHAR(255) NOT NULL,
    requester_name     VARCHAR(150) NOT NULL,
    assigned_admin_id  BIGINT REFERENCES users(id),
    queue_number       INT,
    category           VARCHAR(40)  NOT NULL,
    subject            VARCHAR(200) NOT NULL,
    description        TEXT         NOT NULL,
    status             VARCHAR(20)  NOT NULL DEFAULT 'OPEN',
    channel            VARCHAR(20)  NOT NULL DEFAULT 'ONLINE',
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    resolved_at        TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_tickets_requester_user_created
    ON tickets (requester_user_id, created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_tickets_status
    ON tickets (status);

CREATE INDEX IF NOT EXISTS idx_tickets_assigned_admin
    ON tickets (assigned_admin_id, status);

CREATE INDEX IF NOT EXISTS idx_tickets_queue
    ON tickets (channel, status, queue_number);

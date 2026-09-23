CREATE TABLE IF NOT EXISTS audit_logs (
    id              BIGSERIAL PRIMARY KEY,
    actor_id        BIGINT REFERENCES users(id) ON DELETE SET NULL,
    actor_email     VARCHAR(255),
    actor_name      VARCHAR(150),
    actor_role      VARCHAR(20),
    action          VARCHAR(50)  NOT NULL,
    resource_type   VARCHAR(30)  NOT NULL,
    resource_id     VARCHAR(80),
    resource_label  VARCHAR(200),
    summary         VARCHAR(500) NOT NULL,
    details         TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_audit_logs_created
    ON audit_logs (created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_audit_logs_action
    ON audit_logs (action, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_audit_logs_actor
    ON audit_logs (actor_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_audit_logs_resource
    ON audit_logs (resource_type, resource_id);

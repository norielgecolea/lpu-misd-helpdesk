package org.lpu.dev.codes.helpdesk.config;

import javax.sql.DataSource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Applies idempotent DDL before Hibernate schema validation runs. Docker's
 * postgres init scripts (data/postgres/init/*.sql) only run once, the first
 * time a Postgres data volume is created — this bean instead runs on every
 * app boot so upgrades to an already-running database still get new tables
 * and columns.
 */
@Configuration
public class SchemaMigrationConfig {

    private static final Logger log = LogManager.getLogger(SchemaMigrationConfig.class);

    @Bean
    public SchemaMigrator schemaMigrator(DataSource dataSource) {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS users (
                    id             BIGSERIAL PRIMARY KEY,
                    email          VARCHAR(255) NOT NULL,
                    name           VARCHAR(150) NOT NULL,
                    role           VARCHAR(20)  NOT NULL DEFAULT 'USER',
                    active         BOOLEAN      NOT NULL DEFAULT TRUE,
                    last_login_at  TIMESTAMPTZ,
                    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
                    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
                )
                """);

        // Allow the same email once as a USER and once as staff (admin portal vs helpdesk end-user).
        jdbc.execute("ALTER TABLE users DROP CONSTRAINT IF EXISTS users_email_key");
        jdbc.execute("""
                CREATE UNIQUE INDEX IF NOT EXISTS idx_users_email_user_unique
                    ON users (lower(email))
                    WHERE role = 'USER'
                """);
        jdbc.execute("""
                CREATE UNIQUE INDEX IF NOT EXISTS idx_users_email_staff_unique
                    ON users (lower(email))
                    WHERE role IN ('ADMIN', 'SUPER_ADMIN', 'MONITORING')
                """);

        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS otp_codes (
                    id          BIGSERIAL PRIMARY KEY,
                    email       VARCHAR(255) NOT NULL,
                    code_hash   VARCHAR(255) NOT NULL,
                    expires_at  TIMESTAMPTZ  NOT NULL,
                    consumed    BOOLEAN      NOT NULL DEFAULT FALSE,
                    attempts    INT          NOT NULL DEFAULT 0,
                    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
                )
                """);
        jdbc.execute("""
                CREATE INDEX IF NOT EXISTS idx_otp_codes_email_active
                    ON otp_codes (email, consumed, expires_at DESC)
                """);

        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS tickets (
                    id                 BIGSERIAL PRIMARY KEY,
                    ticket_number      VARCHAR(30)  UNIQUE,
                    requester_user_id  BIGINT REFERENCES users(id),
                    requester_email    VARCHAR(255) NOT NULL,
                    requester_name     VARCHAR(150) NOT NULL,
                    category           VARCHAR(40)  NOT NULL,
                    subject            VARCHAR(200) NOT NULL,
                    description        TEXT         NOT NULL,
                    status             VARCHAR(20)  NOT NULL DEFAULT 'OPEN',
                    channel            VARCHAR(20)  NOT NULL DEFAULT 'ONLINE',
                    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
                    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
                    resolved_at        TIMESTAMPTZ
                )
                """);
        jdbc.execute("""
                CREATE INDEX IF NOT EXISTS idx_tickets_requester_user_created
                    ON tickets (requester_user_id, created_at DESC, id DESC)
                """);
        jdbc.execute("""
                CREATE INDEX IF NOT EXISTS idx_tickets_requester_email_created
                    ON tickets (lower(requester_email), created_at DESC)
                """);
        jdbc.execute("""
                CREATE INDEX IF NOT EXISTS idx_tickets_status
                    ON tickets (status)
                """);

        // --- Admin portal additions ---
        jdbc.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS password_hash VARCHAR(255)");
        jdbc.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS username VARCHAR(50)");
        jdbc.execute("""
                CREATE UNIQUE INDEX IF NOT EXISTS idx_users_username_unique
                    ON users (lower(username))
                    WHERE username IS NOT NULL
                """);
        jdbc.execute("""
                ALTER TABLE users
                    ADD COLUMN IF NOT EXISTS id_verification_status VARCHAR(20) NOT NULL DEFAULT 'NONE'
                """);
        jdbc.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS id_photo_filename VARCHAR(255)");
        jdbc.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS id_uploaded_at TIMESTAMPTZ");
        jdbc.execute("ALTER TABLE tickets ADD COLUMN IF NOT EXISTS assigned_admin_id BIGINT REFERENCES users(id)");
        jdbc.execute("ALTER TABLE tickets ADD COLUMN IF NOT EXISTS queue_number INT");
        jdbc.execute("ALTER TABLE tickets ADD COLUMN IF NOT EXISTS id_photo_filename VARCHAR(255)");
        jdbc.execute("ALTER TABLE tickets ADD COLUMN IF NOT EXISTS email_thread_root_id VARCHAR(255)");
        jdbc.execute("ALTER TABLE tickets ADD COLUMN IF NOT EXISTS requester_person_type VARCHAR(20)");
        jdbc.execute("ALTER TABLE tickets ADD COLUMN IF NOT EXISTS requester_person_no VARCHAR(50)");
        jdbc.execute("""
                CREATE INDEX IF NOT EXISTS idx_tickets_assigned_admin
                    ON tickets (assigned_admin_id, status)
                """);
        jdbc.execute("""
                CREATE INDEX IF NOT EXISTS idx_tickets_queue
                    ON tickets (channel, status, queue_number)
                """);

        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS queue_counters (
                    queue_date      DATE PRIMARY KEY,
                    current_number  INT NOT NULL DEFAULT 0
                )
                """);

        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS ticket_messages (
                    id                        BIGSERIAL PRIMARY KEY,
                    ticket_id                 BIGINT NOT NULL REFERENCES tickets(id) ON DELETE CASCADE,
                    author_user_id            BIGINT REFERENCES users(id),
                    author_email              VARCHAR(255) NOT NULL,
                    author_name               VARCHAR(150) NOT NULL,
                    author_role               VARCHAR(20)  NOT NULL,
                    body                      TEXT NOT NULL DEFAULT '',
                    attachment_filename       VARCHAR(255),
                    attachment_content_type   VARCHAR(100),
                    attachment_original_name  VARCHAR(255),
                    email_message_id          VARCHAR(255),
                    created_at                TIMESTAMPTZ NOT NULL DEFAULT NOW()
                )
                """);
        jdbc.execute("""
                CREATE INDEX IF NOT EXISTS idx_ticket_messages_ticket_created
                    ON ticket_messages (ticket_id, created_at ASC, id ASC)
                """);
        jdbc.execute("ALTER TABLE ticket_messages ADD COLUMN IF NOT EXISTS attachment_filename VARCHAR(255)");
        jdbc.execute("ALTER TABLE ticket_messages ADD COLUMN IF NOT EXISTS attachment_content_type VARCHAR(100)");
        jdbc.execute("ALTER TABLE ticket_messages ADD COLUMN IF NOT EXISTS attachment_original_name VARCHAR(255)");
        jdbc.execute("ALTER TABLE ticket_messages ALTER COLUMN body DROP NOT NULL");
        jdbc.execute("ALTER TABLE ticket_messages ALTER COLUMN body SET DEFAULT ''");

        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS ticket_message_reads (
                    user_id                BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                    ticket_id              BIGINT NOT NULL REFERENCES tickets(id) ON DELETE CASCADE,
                    last_read_message_id   BIGINT NOT NULL DEFAULT 0,
                    updated_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                    PRIMARY KEY (user_id, ticket_id)
                )
                """);

        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS queue_transfer_requests (
                    id              BIGSERIAL PRIMARY KEY,
                    ticket_id       BIGINT NOT NULL REFERENCES tickets(id) ON DELETE CASCADE,
                    from_admin_id   BIGINT NOT NULL REFERENCES users(id),
                    to_admin_id     BIGINT NOT NULL REFERENCES users(id),
                    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                    resolved_at     TIMESTAMPTZ
                )
                """);
        jdbc.execute("""
                CREATE INDEX IF NOT EXISTS idx_queue_transfer_pending_to
                    ON queue_transfer_requests (to_admin_id, status, created_at)
                """);
        jdbc.execute("""
                CREATE UNIQUE INDEX IF NOT EXISTS idx_queue_transfer_one_pending_per_ticket
                    ON queue_transfer_requests (ticket_id)
                    WHERE status = 'PENDING'
                """);

        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS ticket_categories (
                    id               BIGSERIAL PRIMARY KEY,
                    code             VARCHAR(40)  NOT NULL UNIQUE,
                    label            VARCHAR(120) NOT NULL,
                    sort_order       INT          NOT NULL DEFAULT 0,
                    active           BOOLEAN      NOT NULL DEFAULT TRUE,
                    show_on_kiosk    BOOLEAN      NOT NULL DEFAULT TRUE,
                    show_online      BOOLEAN      NOT NULL DEFAULT TRUE,
                    requires_detail  BOOLEAN      NOT NULL DEFAULT FALSE,
                    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
                    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
                )
                """);
        jdbc.execute("""
                CREATE INDEX IF NOT EXISTS idx_tickets_requester_person
                    ON tickets (requester_person_type, requester_person_no)
                """);

        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS ticket_csm (
                    id            BIGSERIAL PRIMARY KEY,
                    ticket_id     BIGINT NOT NULL UNIQUE REFERENCES tickets(id) ON DELETE CASCADE,
                    rating        VARCHAR(20) NOT NULL,
                    comment       TEXT,
                    channel       VARCHAR(20) NOT NULL,
                    submitted_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
                )
                """);
        jdbc.execute("""
                CREATE INDEX IF NOT EXISTS idx_ticket_csm_submitted
                    ON ticket_csm (submitted_at DESC)
                """);

        // Default Super Admin (local/dev only — rotate this password in production):
        //   username: superadmin
        //   email:    superadmin@lpulaguna.edu.ph
        //   password: SuperAdmin@123
        jdbc.execute("""
                INSERT INTO users (email, username, name, role, password_hash, active)
                SELECT
                    'superadmin@lpulaguna.edu.ph',
                    'superadmin',
                    'Super Admin',
                    'SUPER_ADMIN',
                    '$2b$10$3W69f3gej7IOCbPmANPs0u/0beb/oL81rJSqp9c2eJ96vVYKGTrmW',
                    TRUE
                WHERE NOT EXISTS (
                    SELECT 1 FROM users
                    WHERE lower(email) = 'superadmin@lpulaguna.edu.ph'
                      AND role IN ('ADMIN', 'SUPER_ADMIN', 'MONITORING')
                )
                """);
        jdbc.execute("""
                UPDATE users
                SET username = 'superadmin'
                WHERE email = 'superadmin@lpulaguna.edu.ph' AND username IS NULL
                """);
        // Rotate only the previous seeded default password (leave manually changed passwords alone).
        jdbc.execute("""
                UPDATE users
                SET password_hash = '$2b$10$3W69f3gej7IOCbPmANPs0u/0beb/oL81rJSqp9c2eJ96vVYKGTrmW',
                    updated_at = NOW()
                WHERE email = 'superadmin@lpulaguna.edu.ph'
                  AND password_hash = '$2b$10$120MB2Bi4uzy7uocbin1tO4LmSbRrpvnRaNEKRCNwC7t5Pr8b2zsO'
                """);

        // Default Monitoring display account (local/dev only — rotate in production):
        //   username: monitoring
        //   email:    monitoring@lpulaguna.edu.ph
        //   password: Monitoring@2026
        jdbc.execute("""
                INSERT INTO users (email, username, name, role, password_hash, active)
                SELECT
                    'monitoring@lpulaguna.edu.ph',
                    'monitoring',
                    'Monitoring Display',
                    'MONITORING',
                    '$2b$10$JYiW/95KkHkA/pkqCVxgz.zIL6a3wEl4EfyJDvgleDRoSBnSR/W86',
                    TRUE
                WHERE NOT EXISTS (
                    SELECT 1 FROM users
                    WHERE lower(email) = 'monitoring@lpulaguna.edu.ph'
                      AND role IN ('ADMIN', 'SUPER_ADMIN', 'MONITORING')
                )
                """);
        jdbc.execute("""
                UPDATE users
                SET username = 'monitoring'
                WHERE email = 'monitoring@lpulaguna.edu.ph' AND username IS NULL
                """);

        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS password_reset_tokens (
                    id          BIGSERIAL PRIMARY KEY,
                    user_id     BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                    token_hash  VARCHAR(255) NOT NULL,
                    expires_at  TIMESTAMPTZ  NOT NULL,
                    consumed    BOOLEAN      NOT NULL DEFAULT FALSE,
                    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
                )
                """);
        jdbc.execute("""
                CREATE INDEX IF NOT EXISTS idx_password_reset_tokens_user
                    ON password_reset_tokens (user_id, consumed, expires_at DESC)
                """);

        log.info("Schema migration applied (users, otp_codes, tickets, ticket_messages, ticket_message_reads, queue_counters, queue_transfer_requests, ticket_categories, ticket_csm, password_reset_tokens)");
        return new SchemaMigrator();
    }

    /** Marker bean so Hibernate can {@code @DependsOn} migration completion. */
    public static final class SchemaMigrator {
    }
}

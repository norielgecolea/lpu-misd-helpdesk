CREATE TABLE IF NOT EXISTS users (
    id             BIGSERIAL PRIMARY KEY,
    email          VARCHAR(255) NOT NULL,
    username       VARCHAR(50),
    name           VARCHAR(150) NOT NULL,
    password_hash  VARCHAR(255),
    role           VARCHAR(20)  NOT NULL DEFAULT 'USER',
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    last_login_at  TIMESTAMPTZ,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Same person may have a USER (helpdesk) account and a separate staff account.
CREATE UNIQUE INDEX IF NOT EXISTS idx_users_email_user_unique
    ON users (lower(email))
    WHERE role = 'USER';

CREATE UNIQUE INDEX IF NOT EXISTS idx_users_email_staff_unique
    ON users (lower(email))
    WHERE role IN ('ADMIN', 'SUPER_ADMIN', 'MONITORING');

CREATE UNIQUE INDEX IF NOT EXISTS idx_users_username_unique
    ON users (lower(username))
    WHERE username IS NOT NULL;

-- Default Super Admin (local/dev only — rotate this password in production):
--   username: superadmin
--   email:    superadmin@lpulaguna.edu.ph
--   password: SuperAdmin@123
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
);

-- Default Monitoring display account (local/dev only — rotate in production):
--   username: monitoring
--   email:    monitoring@lpulaguna.edu.ph
--   password: Monitoring@2026
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
);

CREATE TABLE IF NOT EXISTS credential_rotation_registry (
    id BIGSERIAL PRIMARY KEY,
    entry_key VARCHAR(255) NOT NULL,
    integration_kind VARCHAR(120) NOT NULL,
    credential_kind VARCHAR(120) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    source_type VARCHAR(120) NOT NULL,
    source_ref TEXT NOT NULL,
    owner_name VARCHAR(255),
    note TEXT,
    source_present BOOLEAN NOT NULL DEFAULT TRUE,
    secret_present BOOLEAN NOT NULL DEFAULT FALSE,
    last_status VARCHAR(80),
    status_level VARCHAR(80),
    status_reason TEXT,
    expires_at TIMESTAMPTZ,
    rotated_at TIMESTAMPTZ,
    rotation_interval_days INTEGER,
    next_rotation_due_at TIMESTAMPTZ,
    last_seen_at TIMESTAMPTZ,
    last_checked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_credential_rotation_registry_entry_key
    ON credential_rotation_registry (entry_key);

CREATE INDEX IF NOT EXISTS idx_credential_rotation_registry_status_level
    ON credential_rotation_registry (status_level);

CREATE INDEX IF NOT EXISTS idx_credential_rotation_registry_integration_kind
    ON credential_rotation_registry (integration_kind);

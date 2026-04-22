CREATE TABLE IF NOT EXISTS rms_license_monitors (
    id BIGSERIAL PRIMARY KEY,
    rms_address VARCHAR(512) NOT NULL,
    scheme VARCHAR(16) NOT NULL DEFAULT 'https',
    host VARCHAR(255) NOT NULL,
    port INT NOT NULL DEFAULT 443,
    auth_login VARCHAR(255) NOT NULL,
    auth_password TEXT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    server_name VARCHAR(255),
    server_type VARCHAR(128),
    server_version VARCHAR(128),
    license_status VARCHAR(32),
    license_error_message TEXT,
    license_expires_at TIMESTAMPTZ,
    license_days_left INT,
    license_last_checked_at TIMESTAMPTZ,
    license_last_notified_at TIMESTAMPTZ,
    rms_status VARCHAR(32),
    rms_status_message TEXT,
    ping_output TEXT,
    traceroute_summary TEXT,
    traceroute_report TEXT,
    traceroute_checked_at TIMESTAMPTZ,
    rms_last_checked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_rms_license_monitors_address
    ON rms_license_monitors(rms_address);

CREATE INDEX IF NOT EXISTS idx_rms_license_monitors_enabled
    ON rms_license_monitors(enabled);

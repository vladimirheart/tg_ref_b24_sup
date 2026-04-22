CREATE TABLE IF NOT EXISTS rms_license_monitors (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    rms_address TEXT NOT NULL,
    scheme TEXT NOT NULL DEFAULT 'https',
    host TEXT NOT NULL,
    port INTEGER NOT NULL DEFAULT 443,
    auth_login TEXT NOT NULL,
    auth_password TEXT NOT NULL,
    enabled INTEGER NOT NULL DEFAULT 1,
    server_name TEXT,
    server_type TEXT,
    server_version TEXT,
    license_status TEXT,
    license_error_message TEXT,
    license_expires_at TEXT,
    license_days_left INTEGER,
    license_last_checked_at TEXT,
    license_last_notified_at TEXT,
    rms_status TEXT,
    rms_status_message TEXT,
    ping_output TEXT,
    traceroute_summary TEXT,
    traceroute_report TEXT,
    traceroute_checked_at TEXT,
    rms_last_checked_at TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_rms_license_monitors_address
    ON rms_license_monitors(rms_address);

CREATE INDEX IF NOT EXISTS idx_rms_license_monitors_enabled
    ON rms_license_monitors(enabled);

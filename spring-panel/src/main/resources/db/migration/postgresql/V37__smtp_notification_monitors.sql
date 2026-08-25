CREATE TABLE IF NOT EXISTS smtp_notification_monitors (
    id BIGSERIAL PRIMARY KEY,
    monitor_name TEXT NOT NULL,
    relay_host TEXT NOT NULL,
    relay_port INTEGER NOT NULL,
    protocol_mode TEXT NOT NULL,
    connect_timeout_ms INTEGER NOT NULL DEFAULT 5000,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    last_status TEXT,
    last_summary TEXT,
    last_error_message TEXT,
    last_banner TEXT,
    last_tls_protocol TEXT,
    last_tls_cipher_suite TEXT,
    last_connected_at TIMESTAMP WITH TIME ZONE,
    last_checked_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_smtp_notification_monitors_name
    ON smtp_notification_monitors (monitor_name);

CREATE UNIQUE INDEX IF NOT EXISTS idx_smtp_notification_monitors_target
    ON smtp_notification_monitors (relay_host, relay_port, protocol_mode);

CREATE INDEX IF NOT EXISTS idx_smtp_notification_monitors_enabled
    ON smtp_notification_monitors (enabled);

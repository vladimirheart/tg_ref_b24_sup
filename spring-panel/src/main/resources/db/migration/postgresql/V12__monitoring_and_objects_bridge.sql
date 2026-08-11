ALTER TABLE rms_license_monitors
    ADD COLUMN IF NOT EXISTS license_monitoring_enabled BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE rms_license_monitors
    ADD COLUMN IF NOT EXISTS license_details_json TEXT;

ALTER TABLE rms_license_monitors
    ADD COLUMN IF NOT EXISTS license_debug_excerpt TEXT;

ALTER TABLE rms_license_monitors
    ADD COLUMN IF NOT EXISTS network_monitoring_enabled BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE rms_license_monitors
    ADD COLUMN IF NOT EXISTS is_deleted BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE rms_license_monitors
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP WITH TIME ZONE;

CREATE TABLE IF NOT EXISTS ssl_certificate_monitors (
    id BIGSERIAL PRIMARY KEY,
    site_name TEXT NOT NULL,
    endpoint_url TEXT NOT NULL,
    host TEXT NOT NULL,
    port INTEGER NOT NULL DEFAULT 443,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    monitor_status TEXT,
    error_message TEXT,
    days_left INTEGER,
    expires_at TIMESTAMP WITH TIME ZONE,
    last_checked_at TIMESTAMP WITH TIME ZONE,
    last_notified_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_ssl_certificate_monitors_endpoint
    ON ssl_certificate_monitors(endpoint_url);

CREATE INDEX IF NOT EXISTS idx_ssl_certificate_monitors_enabled
    ON ssl_certificate_monitors(enabled);

CREATE TABLE IF NOT EXISTS rms_refresh_queue (
    id BIGSERIAL PRIMARY KEY,
    queue_kind TEXT NOT NULL,
    monitor_id BIGINT,
    with_notifications BOOLEAN NOT NULL DEFAULT FALSE,
    status TEXT NOT NULL DEFAULT 'queued',
    requested_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_rms_refresh_queue_kind_status
    ON rms_refresh_queue(queue_kind, status, requested_at, id);

CREATE TABLE IF NOT EXISTS iiko_api_monitors (
    id BIGSERIAL PRIMARY KEY,
    monitor_name TEXT NOT NULL,
    base_url TEXT NOT NULL,
    api_login TEXT NOT NULL,
    request_type TEXT NOT NULL,
    request_config_json TEXT,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    locations_sync_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    last_status TEXT,
    last_http_status INTEGER,
    last_error_message TEXT,
    last_duration_ms BIGINT,
    last_checked_at TIMESTAMP WITH TIME ZONE,
    last_token_checked_at TIMESTAMP WITH TIME ZONE,
    last_response_excerpt TEXT,
    last_response_summary_json TEXT,
    consecutive_failures INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_iiko_api_monitors_enabled
    ON iiko_api_monitors(enabled);

CREATE INDEX IF NOT EXISTS idx_iiko_api_monitors_request_type
    ON iiko_api_monitors(request_type);

CREATE TABLE IF NOT EXISTS monitoring_check_history (
    id BIGSERIAL PRIMARY KEY,
    monitor_kind TEXT NOT NULL,
    monitor_id BIGINT NOT NULL,
    check_kind TEXT NOT NULL,
    status TEXT,
    summary TEXT,
    details_excerpt TEXT,
    http_status INTEGER,
    duration_ms BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_monitoring_check_history_monitor
    ON monitoring_check_history(monitor_kind, monitor_id, created_at DESC, id DESC);

CREATE TABLE IF NOT EXISTS objects (
    id BIGSERIAL PRIMARY KEY,
    name TEXT NOT NULL,
    address TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS object_passports (
    id BIGSERIAL PRIMARY KEY,
    object_id BIGINT NOT NULL REFERENCES objects(id),
    passport_number TEXT,
    details TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_object_passports_object_id
    ON object_passports(object_id);

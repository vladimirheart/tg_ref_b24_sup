CREATE TABLE IF NOT EXISTS backup_readiness_monitors (
    id BIGSERIAL PRIMARY KEY,
    monitor_name TEXT NOT NULL,
    backup_kind TEXT NOT NULL DEFAULT 'generic',
    path_pattern TEXT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    freshness_threshold_hours INTEGER NOT NULL DEFAULT 24,
    restore_threshold_days INTEGER NOT NULL DEFAULT 14,
    last_status TEXT,
    last_summary TEXT,
    last_error_message TEXT,
    last_backup_at TIMESTAMP WITH TIME ZONE,
    last_backup_size_bytes BIGINT,
    last_backup_path TEXT,
    last_restore_verified_at TIMESTAMP WITH TIME ZONE,
    last_restore_note TEXT,
    last_checked_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_backup_readiness_monitors_name
    ON backup_readiness_monitors (monitor_name);

CREATE INDEX IF NOT EXISTS idx_backup_readiness_monitors_enabled
    ON backup_readiness_monitors (enabled);

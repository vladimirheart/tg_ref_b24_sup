CREATE TABLE IF NOT EXISTS backend_ops_command (
    command_id VARCHAR(64) PRIMARY KEY,
    command_type VARCHAR(120) NOT NULL,
    scope_key VARCHAR(240) NOT NULL,
    active_key VARCHAR(160) UNIQUE,
    payload_json TEXT NOT NULL DEFAULT '{}',
    status VARCHAR(32) NOT NULL,
    requested_by VARCHAR(160),
    requested_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    available_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    claimed_by VARCHAR(160),
    claimed_at TIMESTAMPTZ,
    heartbeat_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    progress_percent INTEGER NOT NULL DEFAULT 0,
    progress_message TEXT,
    result_json TEXT,
    last_error TEXT,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_backend_ops_command_status_available
    ON backend_ops_command(status, available_at, requested_at);

CREATE INDEX IF NOT EXISTS idx_backend_ops_command_type_requested
    ON backend_ops_command(command_type, requested_at DESC);

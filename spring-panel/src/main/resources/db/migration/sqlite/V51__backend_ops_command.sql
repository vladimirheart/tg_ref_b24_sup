CREATE TABLE IF NOT EXISTS backend_ops_command (
    command_id TEXT PRIMARY KEY,
    command_type TEXT NOT NULL,
    scope_key TEXT NOT NULL,
    active_key TEXT UNIQUE,
    payload_json TEXT NOT NULL DEFAULT '{}',
    status TEXT NOT NULL,
    requested_by TEXT,
    requested_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    available_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    claimed_by TEXT,
    claimed_at TIMESTAMP,
    heartbeat_at TIMESTAMP,
    completed_at TIMESTAMP,
    progress_percent INTEGER NOT NULL DEFAULT 0,
    progress_message TEXT,
    result_json TEXT,
    last_error TEXT,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_backend_ops_command_status_available
    ON backend_ops_command(status, available_at, requested_at);

CREATE INDEX IF NOT EXISTS idx_backend_ops_command_type_requested
    ON backend_ops_command(command_type, requested_at DESC);

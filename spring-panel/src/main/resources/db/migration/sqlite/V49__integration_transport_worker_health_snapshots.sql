CREATE TABLE IF NOT EXISTS integration_transport_worker_health_snapshots (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    worker_key TEXT NOT NULL,
    worker_label TEXT,
    source_table TEXT,
    checkpoint_updated_at TEXT,
    cursor_text TEXT,
    source_max_cursor INTEGER,
    cursor_lag INTEGER,
    age_minutes INTEGER,
    stale_threshold_minutes INTEGER NOT NULL DEFAULT 0,
    lag_alert_threshold INTEGER NOT NULL DEFAULT 0,
    health_status TEXT NOT NULL,
    stale INTEGER NOT NULL DEFAULT 0,
    unhealthy INTEGER NOT NULL DEFAULT 0,
    summary_text TEXT,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_transport_worker_health_snapshots_worker_created
    ON integration_transport_worker_health_snapshots(worker_key, created_at DESC, id DESC);

CREATE TABLE IF NOT EXISTS integration_transport_health_snapshots (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    inbound_failed INTEGER NOT NULL DEFAULT 0,
    inbound_stale_processing INTEGER NOT NULL DEFAULT 0,
    outbound_failed INTEGER NOT NULL DEFAULT 0,
    outbound_backlog INTEGER NOT NULL DEFAULT 0,
    outbound_stale_processing INTEGER NOT NULL DEFAULT 0,
    stale_checkpoint_count INTEGER NOT NULL DEFAULT 0,
    lagging_checkpoint_count INTEGER NOT NULL DEFAULT 0,
    recent_manual_operations INTEGER NOT NULL DEFAULT 0,
    severity TEXT NOT NULL,
    summary_text TEXT NOT NULL,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_integration_transport_health_snapshots_created
    ON integration_transport_health_snapshots(created_at DESC, id DESC);

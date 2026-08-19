CREATE TABLE IF NOT EXISTS integration_transport_worker_health_snapshots (
    id BIGSERIAL PRIMARY KEY,
    worker_key VARCHAR(120) NOT NULL,
    worker_label VARCHAR(255),
    source_table VARCHAR(120),
    checkpoint_updated_at TIMESTAMPTZ,
    cursor_text VARCHAR(255),
    source_max_cursor BIGINT,
    cursor_lag BIGINT,
    age_minutes BIGINT,
    stale_threshold_minutes BIGINT NOT NULL DEFAULT 0,
    lag_alert_threshold BIGINT NOT NULL DEFAULT 0,
    health_status VARCHAR(32) NOT NULL,
    stale BOOLEAN NOT NULL DEFAULT FALSE,
    unhealthy BOOLEAN NOT NULL DEFAULT FALSE,
    summary_text TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_transport_worker_health_snapshots_worker_created
    ON integration_transport_worker_health_snapshots(worker_key, created_at DESC, id DESC);

CREATE TABLE IF NOT EXISTS integration_transport_outbox (
    event_id TEXT PRIMARY KEY,
    transport_source TEXT NOT NULL,
    event_kind TEXT NOT NULL,
    exchange_name TEXT NOT NULL,
    routing_key TEXT NOT NULL,
    payload_json TEXT NOT NULL,
    channel_id BIGINT,
    user_id BIGINT,
    ticket_id TEXT,
    request_id BIGINT,
    status TEXT NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    last_error TEXT,
    available_at TEXT,
    processing_started_at TEXT,
    published_at TEXT,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_integration_transport_outbox_status
    ON integration_transport_outbox(status, available_at, updated_at);

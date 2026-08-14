CREATE TABLE IF NOT EXISTS integration_inbound_event_inbox (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    event_id TEXT NOT NULL UNIQUE,
    event_kind TEXT NOT NULL,
    platform TEXT NOT NULL,
    channel_id BIGINT NOT NULL,
    ticket_id TEXT NOT NULL,
    transport_source TEXT NOT NULL,
    routing_key TEXT,
    payload_json TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'received',
    received_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TEXT,
    last_error TEXT
);

CREATE INDEX IF NOT EXISTS idx_integration_inbound_event_inbox_status
    ON integration_inbound_event_inbox(status, received_at, id);

CREATE INDEX IF NOT EXISTS idx_integration_inbound_event_inbox_ticket
    ON integration_inbound_event_inbox(channel_id, ticket_id, received_at, id);

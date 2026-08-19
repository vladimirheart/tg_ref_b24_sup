CREATE TABLE IF NOT EXISTS ui_event_outbox (
    id BIGINT PRIMARY KEY,
    event_type TEXT NOT NULL,
    ticket_id TEXT NOT NULL,
    channel_id BIGINT,
    message_text TEXT,
    message_type TEXT,
    attachment TEXT,
    rating INTEGER,
    created_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_ui_event_outbox_ticket
    ON ui_event_outbox(ticket_id, id);

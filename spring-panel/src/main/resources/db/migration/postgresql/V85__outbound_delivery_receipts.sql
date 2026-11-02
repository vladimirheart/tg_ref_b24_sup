-- Canonical ownership of the previously bot-created delivery ledger; existing rows stay intact.
CREATE TABLE IF NOT EXISTS integration_outbound_event_deliveries (
    event_id TEXT PRIMARY KEY, event_kind TEXT, routing_key TEXT, channel_id BIGINT,
    user_id BIGINT, ticket_id TEXT, request_id BIGINT, status TEXT NOT NULL,
    last_error TEXT, created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL,
    delivered_at TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_outbound_event_deliveries_status
    ON integration_outbound_event_deliveries(status, updated_at);

CREATE TABLE integration_outbound_delivery_receipts (
    event_id TEXT PRIMARY KEY REFERENCES integration_outbound_event_deliveries(event_id),
    attempt_id UUID NOT NULL UNIQUE,
    event_kind TEXT, routing_key TEXT, channel_id BIGINT NOT NULL CHECK (channel_id > 0), user_id BIGINT NOT NULL CHECK (user_id > 0),
    ticket_id TEXT, request_id BIGINT,
    platform TEXT NOT NULL CHECK (platform IN ('telegram','vk','max')),
    sender_fingerprint TEXT NOT NULL CHECK (sender_fingerprint ~ '^[0-9a-f]{64}$'),
    prompt_sha256 TEXT NOT NULL CHECK (prompt_sha256 ~ '^[0-9a-f]{64}$'),
    provider_message_id TEXT CHECK (length(provider_message_id) BETWEEN 1 AND 256 AND provider_message_id ~ '^[A-Za-z0-9._:-]+$'),
    prepared_at TIMESTAMP NOT NULL,
    accepted_at TIMESTAMP,
    CHECK ((provider_message_id IS NULL) = (accepted_at IS NULL))
);
COMMENT ON TABLE integration_outbound_delivery_receipts IS
    'Private attempt-bound provider acceptance, not a client read receipt; no automatic resend permission';

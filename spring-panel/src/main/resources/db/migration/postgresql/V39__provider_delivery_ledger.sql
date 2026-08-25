CREATE TABLE IF NOT EXISTS provider_delivery_ledger (
    id BIGSERIAL PRIMARY KEY,
    channel_id BIGINT NOT NULL,
    ticket_id VARCHAR(255),
    platform VARCHAR(80) NOT NULL,
    provider VARCHAR(80) NOT NULL,
    user_id BIGINT,
    sender_kind VARCHAR(80) NOT NULL,
    message_kind VARCHAR(80) NOT NULL,
    delivery_status VARCHAR(80) NOT NULL,
    classification VARCHAR(80) NOT NULL,
    severity_level VARCHAR(80) NOT NULL,
    retry_state VARCHAR(80) NOT NULL,
    http_status INTEGER,
    provider_error_code VARCHAR(255),
    provider_message TEXT,
    response_excerpt TEXT,
    provider_message_id BIGINT,
    reply_to_message_id BIGINT,
    duration_ms BIGINT,
    attempted_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_provider_delivery_ledger_channel_attempted_at
    ON provider_delivery_ledger (channel_id, attempted_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_provider_delivery_ledger_attempted_at
    ON provider_delivery_ledger (attempted_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_provider_delivery_ledger_classification
    ON provider_delivery_ledger (classification);

CREATE INDEX IF NOT EXISTS idx_provider_delivery_ledger_severity
    ON provider_delivery_ledger (severity_level);

CREATE TABLE IF NOT EXISTS integration_transport_operation_log (
    id BIGSERIAL PRIMARY KEY,
    action_type TEXT NOT NULL,
    summary_text TEXT NOT NULL,
    target_type TEXT NOT NULL,
    target_id TEXT,
    ticket_id TEXT,
    worker_key TEXT,
    result_status TEXT NOT NULL DEFAULT 'success',
    actor TEXT,
    details_json TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_integration_transport_operation_log_created
    ON integration_transport_operation_log(created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_integration_transport_operation_log_target
    ON integration_transport_operation_log(target_type, target_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_integration_transport_operation_log_ticket
    ON integration_transport_operation_log(ticket_id, created_at DESC);

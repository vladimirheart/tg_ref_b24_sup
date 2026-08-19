CREATE TABLE IF NOT EXISTS incident_route_delivery_outbox (
    event_id TEXT PRIMARY KEY,
    incident_id BIGINT NOT NULL REFERENCES incidents(id) ON DELETE CASCADE,
    route_id BIGINT NOT NULL REFERENCES incident_routes(id) ON DELETE CASCADE,
    event_type TEXT NOT NULL,
    route_type TEXT NOT NULL,
    route_target TEXT NOT NULL,
    message_text TEXT NOT NULL,
    incident_url TEXT,
    payload_json TEXT NOT NULL,
    requested_by TEXT,
    status TEXT NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    last_error TEXT,
    available_at TIMESTAMPTZ,
    processing_started_at TIMESTAMPTZ,
    delivered_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_incident_route_delivery_outbox_status
    ON incident_route_delivery_outbox(status, available_at, updated_at);

CREATE INDEX IF NOT EXISTS idx_incident_route_delivery_outbox_incident_route
    ON incident_route_delivery_outbox(incident_id, route_id, created_at DESC);

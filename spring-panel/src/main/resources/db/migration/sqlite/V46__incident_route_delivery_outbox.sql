CREATE TABLE IF NOT EXISTS incident_route_delivery_outbox (
    event_id TEXT PRIMARY KEY,
    incident_id INTEGER NOT NULL,
    route_id INTEGER NOT NULL,
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
    available_at TEXT,
    processing_started_at TEXT,
    delivered_at TEXT,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_incident_route_delivery_outbox_incident
        FOREIGN KEY (incident_id) REFERENCES incidents(id) ON DELETE CASCADE,
    CONSTRAINT fk_incident_route_delivery_outbox_route
        FOREIGN KEY (route_id) REFERENCES incident_routes(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_incident_route_delivery_outbox_status
    ON incident_route_delivery_outbox(status, available_at, updated_at);

CREATE INDEX IF NOT EXISTS idx_incident_route_delivery_outbox_incident_route
    ON incident_route_delivery_outbox(incident_id, route_id, created_at DESC);

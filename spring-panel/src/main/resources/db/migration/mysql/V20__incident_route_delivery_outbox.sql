CREATE TABLE IF NOT EXISTS incident_route_delivery_outbox (
    event_id VARCHAR(120) PRIMARY KEY,
    incident_id BIGINT NOT NULL,
    route_id BIGINT NOT NULL,
    event_type VARCHAR(120) NOT NULL,
    route_type VARCHAR(120) NOT NULL,
    route_target TEXT NOT NULL,
    message_text TEXT NOT NULL,
    incident_url TEXT,
    payload_json LONGTEXT NOT NULL,
    requested_by VARCHAR(120),
    status VARCHAR(32) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    last_error LONGTEXT,
    available_at DATETIME(6),
    processing_started_at DATETIME(6),
    delivered_at DATETIME(6),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_incident_route_delivery_outbox_incident FOREIGN KEY (incident_id) REFERENCES incidents(id) ON DELETE CASCADE,
    CONSTRAINT fk_incident_route_delivery_outbox_route FOREIGN KEY (route_id) REFERENCES incident_routes(id) ON DELETE CASCADE
);

CREATE INDEX idx_incident_route_delivery_outbox_status
    ON incident_route_delivery_outbox(status, available_at, updated_at);

CREATE INDEX idx_incident_route_delivery_outbox_incident_route
    ON incident_route_delivery_outbox(incident_id, route_id, created_at);

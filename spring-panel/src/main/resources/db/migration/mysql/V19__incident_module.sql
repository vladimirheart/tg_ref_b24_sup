CREATE TABLE IF NOT EXISTS incidents (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    incident_key VARCHAR(255) NOT NULL UNIQUE,
    title TEXT NOT NULL,
    summary TEXT NULL,
    description TEXT NULL,
    status VARCHAR(64) NOT NULL DEFAULT 'open',
    severity VARCHAR(64) NOT NULL DEFAULT 'medium',
    source VARCHAR(255) NULL,
    signal_type VARCHAR(255) NULL,
    signal_key VARCHAR(255) NULL,
    owner VARCHAR(255) NULL,
    created_by VARCHAR(255) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    acknowledged_at DATETIME(6) NULL,
    resolved_at DATETIME(6) NULL
);

CREATE TABLE IF NOT EXISTS incident_relations (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    incident_id BIGINT NOT NULL,
    relation_type VARCHAR(64) NOT NULL,
    relation_key VARCHAR(255) NOT NULL,
    relation_label TEXT NULL,
    metadata_json TEXT NULL,
    primary_relation BIT(1) NOT NULL DEFAULT b'0',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by VARCHAR(255) NULL,
    CONSTRAINT uq_incident_relation UNIQUE (incident_id, relation_type, relation_key),
    CONSTRAINT fk_incident_relations_incident FOREIGN KEY (incident_id) REFERENCES incidents(id) ON DELETE CASCADE
);

CREATE INDEX idx_incident_relations_relation_lookup
    ON incident_relations(relation_type, relation_key);

CREATE TABLE IF NOT EXISTS incident_events (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    incident_id BIGINT NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    event_text TEXT NOT NULL,
    payload_json TEXT NULL,
    actor VARCHAR(255) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_incident_events_incident FOREIGN KEY (incident_id) REFERENCES incidents(id) ON DELETE CASCADE
);

CREATE INDEX idx_incident_events_incident_created
    ON incident_events(incident_id, created_at);

CREATE TABLE IF NOT EXISTS incident_watchers (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    incident_id BIGINT NOT NULL,
    watcher_identity VARCHAR(255) NOT NULL,
    added_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    added_by VARCHAR(255) NULL,
    CONSTRAINT uq_incident_watcher UNIQUE (incident_id, watcher_identity),
    CONSTRAINT fk_incident_watchers_incident FOREIGN KEY (incident_id) REFERENCES incidents(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS incident_routes (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    incident_id BIGINT NOT NULL,
    route_type VARCHAR(255) NOT NULL,
    route_target VARCHAR(255) NOT NULL,
    route_status VARCHAR(255) NULL,
    note TEXT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_incident_routes_incident FOREIGN KEY (incident_id) REFERENCES incidents(id) ON DELETE CASCADE
);

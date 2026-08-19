CREATE TABLE IF NOT EXISTS incidents (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    incident_key TEXT NOT NULL UNIQUE,
    title TEXT NOT NULL,
    summary TEXT,
    description TEXT,
    status TEXT NOT NULL DEFAULT 'open',
    severity TEXT NOT NULL DEFAULT 'medium',
    source TEXT,
    signal_type TEXT,
    signal_key TEXT,
    owner TEXT,
    created_by TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    acknowledged_at TIMESTAMP WITH TIME ZONE,
    resolved_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE IF NOT EXISTS incident_relations (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    incident_id BIGINT NOT NULL REFERENCES incidents(id) ON DELETE CASCADE,
    relation_type TEXT NOT NULL,
    relation_key TEXT NOT NULL,
    relation_label TEXT,
    metadata_json TEXT,
    primary_relation BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by TEXT,
    CONSTRAINT uq_incident_relation UNIQUE (incident_id, relation_type, relation_key)
);

CREATE INDEX IF NOT EXISTS idx_incident_relations_relation_lookup
    ON incident_relations(relation_type, relation_key);

CREATE TABLE IF NOT EXISTS incident_events (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    incident_id BIGINT NOT NULL REFERENCES incidents(id) ON DELETE CASCADE,
    event_type TEXT NOT NULL,
    event_text TEXT NOT NULL,
    payload_json TEXT,
    actor TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_incident_events_incident_created
    ON incident_events(incident_id, created_at);

CREATE TABLE IF NOT EXISTS incident_watchers (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    incident_id BIGINT NOT NULL REFERENCES incidents(id) ON DELETE CASCADE,
    watcher_identity TEXT NOT NULL,
    added_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    added_by TEXT,
    CONSTRAINT uq_incident_watcher UNIQUE (incident_id, watcher_identity)
);

CREATE TABLE IF NOT EXISTS incident_routes (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    incident_id BIGINT NOT NULL REFERENCES incidents(id) ON DELETE CASCADE,
    route_type TEXT NOT NULL,
    route_target TEXT NOT NULL,
    route_status TEXT,
    note TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Explicit manual provenance; preserve every existing owner and selection source.
CREATE FUNCTION general_ticket_selection_source_allowed(p_source TEXT) RETURNS BOOLEAN
    LANGUAGE SQL IMMUTABLE AS $$
    SELECT p_source IS NOT NULL AND p_source IN ('USER_SELECTION','GENERAL_CHANNEL','MANUAL')
$$;
ALTER TABLE tickets DROP CONSTRAINT chk_tickets_general_scope;
ALTER TABLE tickets ADD CONSTRAINT chk_tickets_general_scope CHECK (
    (is_general=FALSE AND general_selection_source IS NULL)
    OR (is_general=TRUE AND business_id IS NULL AND general_ticket_selection_source_allowed(general_selection_source))
);

-- Share the non-cycling V54 sequence; never infer or import business-local clients.
CREATE TABLE manual_general_clients (
    user_id BIGINT PRIMARY KEY CHECK (user_id < 0),
    display_name VARCHAR(200) NOT NULL CHECK (btrim(display_name) <> ''),
    created_by_user_id BIGINT NOT NULL CHECK (created_by_user_id > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE general_manual_ticket_requests (
    request_key VARCHAR(32) PRIMARY KEY CHECK (request_key ~ '^[0-9a-f]{32}$'),
    actor_user_id BIGINT NOT NULL CHECK (actor_user_id > 0),
    actor TEXT NOT NULL,
    input_sha256 VARCHAR(64) NOT NULL CHECK (input_sha256 ~ '^[0-9a-f]{64}$'),
    ticket_user_id BIGINT NOT NULL CHECK (ticket_user_id < 0),
    ticket_id TEXT NOT NULL CHECK (btrim(ticket_id) <> ''),
    ticket_public_id VARCHAR(32) NOT NULL UNIQUE CHECK (ticket_public_id ~ '^[0-9a-f]{32}$'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(ticket_user_id,ticket_id,ticket_public_id)
);
CREATE TRIGGER general_manual_request_immutable BEFORE UPDATE ON general_manual_ticket_requests
    FOR EACH ROW EXECUTE FUNCTION prevent_scoped_ai_event_change();

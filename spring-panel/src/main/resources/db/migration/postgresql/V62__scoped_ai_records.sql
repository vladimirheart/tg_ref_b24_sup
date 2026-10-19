-- Canonical AI records are separate from unattributed legacy AI tables. No backfill.
CREATE TABLE ticket_ai_scoped_control (
    ticket_user_id BIGINT NOT NULL,
    ticket_id TEXT NOT NULL CHECK (btrim(ticket_id) <> ''),
    ticket_public_id VARCHAR(32) NOT NULL CHECK (ticket_public_id ~ '^[0-9a-f]{32}$'),
    ticket_scope_kind VARCHAR(20) NOT NULL,
    ticket_business_id BIGINT,
    scope_business_key BIGINT GENERATED ALWAYS AS (COALESCE(ticket_business_id, 0)) STORED,
    ai_disabled BOOLEAN NOT NULL,
    auto_reply_blocked BOOLEAN NOT NULL,
    reason TEXT,
    updated_by_user_id BIGINT NOT NULL,
    updated_by TEXT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (ticket_public_id, scope_business_key),
    CHECK ((ticket_scope_kind = 'BUSINESS' AND ticket_business_id IS NOT NULL AND ticket_business_id > 0)
        OR (ticket_scope_kind = 'UNRESOLVED' AND ticket_business_id IS NULL))
);

CREATE TABLE ticket_ai_scoped_state (
    ticket_user_id BIGINT NOT NULL,
    ticket_id TEXT NOT NULL CHECK (btrim(ticket_id) <> ''),
    ticket_public_id VARCHAR(32) NOT NULL CHECK (ticket_public_id ~ '^[0-9a-f]{32}$'),
    ticket_scope_kind VARCHAR(20) NOT NULL,
    ticket_business_id BIGINT,
    scope_business_key BIGINT GENERATED ALWAYS AS (COALESCE(ticket_business_id, 0)) STORED,
    is_processing BOOLEAN NOT NULL,
    mode TEXT NOT NULL,
    last_action TEXT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (ticket_public_id, scope_business_key),
    CHECK ((ticket_scope_kind = 'BUSINESS' AND ticket_business_id IS NOT NULL AND ticket_business_id > 0)
        OR (ticket_scope_kind = 'UNRESOLVED' AND ticket_business_id IS NULL))
);

CREATE TABLE ticket_ai_scoped_events (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ticket_user_id BIGINT NOT NULL,
    ticket_id TEXT NOT NULL CHECK (btrim(ticket_id) <> ''),
    ticket_public_id VARCHAR(32) NOT NULL CHECK (ticket_public_id ~ '^[0-9a-f]{32}$'),
    ticket_scope_kind VARCHAR(20) NOT NULL,
    ticket_business_id BIGINT,
    event_type TEXT NOT NULL,
    decision_reason TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK ((ticket_scope_kind = 'BUSINESS' AND ticket_business_id IS NOT NULL AND ticket_business_id > 0)
        OR (ticket_scope_kind = 'UNRESOLVED' AND ticket_business_id IS NULL))
);
CREATE INDEX idx_ticket_ai_scoped_events_owner ON ticket_ai_scoped_events(ticket_public_id, ticket_business_id, id DESC);

CREATE FUNCTION prevent_scoped_ai_owner_change()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF ROW(OLD.ticket_user_id, OLD.ticket_id, OLD.ticket_public_id, OLD.ticket_scope_kind, OLD.ticket_business_id)
       IS DISTINCT FROM
       ROW(NEW.ticket_user_id, NEW.ticket_id, NEW.ticket_public_id, NEW.ticket_scope_kind, NEW.ticket_business_id) THEN
        RAISE EXCEPTION 'Scoped AI owner is immutable' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_scoped_ai_control_owner BEFORE UPDATE ON ticket_ai_scoped_control
    FOR EACH ROW EXECUTE FUNCTION prevent_scoped_ai_owner_change();
CREATE TRIGGER trg_scoped_ai_state_owner BEFORE UPDATE ON ticket_ai_scoped_state
    FOR EACH ROW EXECUTE FUNCTION prevent_scoped_ai_owner_change();

CREATE FUNCTION prevent_scoped_ai_event_change()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Scoped AI event is immutable' USING ERRCODE = '23514';
END;
$$;
CREATE TRIGGER trg_scoped_ai_event_immutable BEFORE UPDATE ON ticket_ai_scoped_events
    FOR EACH ROW EXECUTE FUNCTION prevent_scoped_ai_event_change();

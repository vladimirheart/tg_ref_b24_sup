-- New curated sources only. Legacy text scopes and global sources are not classified or copied.
CREATE TABLE ai_scoped_sources (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    business_id BIGINT NOT NULL REFERENCES businesses(id),
    source_kind VARCHAR(20) NOT NULL CHECK (source_kind IN ('KNOWLEDGE','MEMORY')),
    source_key TEXT NOT NULL CHECK (btrim(source_key) <> '' AND length(source_key) <= 200),
    title TEXT NOT NULL CHECK (btrim(title) <> '' AND length(title) <= 300),
    body TEXT NOT NULL CHECK (btrim(body) <> '' AND length(body) <= 16000),
    status VARCHAR(20) NOT NULL CHECK (status IN ('DRAFT','APPROVED','ARCHIVED')),
    revision BIGINT NOT NULL CHECK (revision > 0),
    origin_ticket_user_id BIGINT,
    origin_ticket_id TEXT,
    origin_ticket_public_id VARCHAR(32),
    origin_ticket_scope_kind VARCHAR(20),
    origin_ticket_business_id BIGINT,
    updated_by_user_id BIGINT NOT NULL,
    updated_by TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (business_id,source_kind,source_key),
    CHECK (business_id > 0),
    CONSTRAINT chk_ai_scoped_source_origin CHECK (
        (source_kind='KNOWLEDGE' AND origin_ticket_user_id IS NULL AND origin_ticket_id IS NULL
            AND origin_ticket_public_id IS NULL AND origin_ticket_scope_kind IS NULL AND origin_ticket_business_id IS NULL)
        OR (source_kind='MEMORY' AND origin_ticket_user_id IS NOT NULL AND origin_ticket_id IS NOT NULL
            AND btrim(origin_ticket_id) <> '' AND origin_ticket_public_id IS NOT NULL
            AND origin_ticket_public_id ~ '^[0-9a-f]{32}$' AND origin_ticket_scope_kind IS NOT NULL
            AND origin_ticket_scope_kind='BUSINESS' AND origin_ticket_business_id IS NOT NULL
            AND origin_ticket_business_id=business_id)
    )
);
CREATE INDEX idx_ai_scoped_sources_candidates ON ai_scoped_sources(business_id,status,updated_at DESC,id DESC);

CREATE TABLE ai_scoped_source_events (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source_id BIGINT NOT NULL,
    business_id BIGINT NOT NULL REFERENCES businesses(id),
    source_kind VARCHAR(20) NOT NULL CHECK (source_kind IN ('KNOWLEDGE','MEMORY')),
    source_key TEXT NOT NULL,
    revision BIGINT NOT NULL CHECK (revision > 0),
    status VARCHAR(20) NOT NULL CHECK (status IN ('DRAFT','APPROVED','ARCHIVED')),
    action VARCHAR(20) NOT NULL CHECK (action IN ('create','revise')),
    actor_user_id BIGINT NOT NULL,
    actor TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (source_id,revision)
);
CREATE INDEX idx_ai_scoped_source_events_owner ON ai_scoped_source_events(business_id,source_id,id DESC);

CREATE FUNCTION prevent_scoped_ai_source_identity_change()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF ROW(OLD.id,OLD.business_id,OLD.source_kind,OLD.source_key,OLD.origin_ticket_user_id,OLD.origin_ticket_id,
           OLD.origin_ticket_public_id,OLD.origin_ticket_scope_kind,OLD.origin_ticket_business_id,OLD.created_at)
       IS DISTINCT FROM
       ROW(NEW.id,NEW.business_id,NEW.source_kind,NEW.source_key,NEW.origin_ticket_user_id,NEW.origin_ticket_id,
           NEW.origin_ticket_public_id,NEW.origin_ticket_scope_kind,NEW.origin_ticket_business_id,NEW.created_at) THEN
        RAISE EXCEPTION 'Scoped AI source identity is immutable' USING ERRCODE='23514';
    END IF;
    IF NEW.revision <> OLD.revision+1 THEN
        RAISE EXCEPTION 'Scoped AI source revision must advance by one' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_scoped_ai_source_identity BEFORE UPDATE ON ai_scoped_sources
    FOR EACH ROW EXECUTE FUNCTION prevent_scoped_ai_source_identity_change();
CREATE TRIGGER trg_scoped_ai_source_event_immutable BEFORE UPDATE ON ai_scoped_source_events
    FOR EACH ROW EXECUTE FUNCTION prevent_scoped_ai_event_change();

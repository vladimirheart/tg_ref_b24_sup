-- Explicit opt-in; READ and business/system roles never imply OPERATE.
CREATE TABLE general_ticket_operate_grants (
    user_id BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
ALTER TABLE tickets ADD COLUMN general_work_revision BIGINT NOT NULL DEFAULT 0 CHECK (general_work_revision >= 0);
CREATE FUNCTION maintain_general_work_revision() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        IF NEW.general_work_revision <> 0 THEN RAISE EXCEPTION 'Initial GENERAL revision must be zero' USING ERRCODE='23514'; END IF;
        RETURN NEW;
    END IF;
    IF NEW.general_work_revision <> OLD.general_work_revision THEN
        RAISE EXCEPTION 'GENERAL revision is maintained by PostgreSQL' USING ERRCODE='23514';
    END IF;
    IF (OLD.is_general OR NEW.is_general) AND
       ROW(OLD.user_id,OLD.ticket_id,OLD.ticket_public_id,OLD.is_general,OLD.business_id,OLD.status)
       IS DISTINCT FROM ROW(NEW.user_id,NEW.ticket_id,NEW.ticket_public_id,NEW.is_general,NEW.business_id,NEW.status) THEN
        NEW.general_work_revision := OLD.general_work_revision + 1;
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_general_work_revision BEFORE INSERT OR UPDATE ON tickets
    FOR EACH ROW EXECUTE FUNCTION maintain_general_work_revision();

CREATE TABLE general_ticket_work_events (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ticket_user_id BIGINT NOT NULL,
    ticket_id TEXT NOT NULL CHECK (btrim(ticket_id) <> ''),
    ticket_public_id VARCHAR(32) NOT NULL CHECK (ticket_public_id ~ '^[0-9a-f]{32}$'),
    revision BIGINT NOT NULL CHECK (revision > 0),
    from_status VARCHAR(20) NOT NULL CHECK (from_status IN ('pending','open')),
    to_status VARCHAR(20) NOT NULL CHECK (to_status IN ('pending','open') AND to_status <> from_status),
    actor_user_id BIGINT NOT NULL CHECK (actor_user_id > 0),
    actor TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(ticket_public_id,revision)
);
-- Historical owner/actor facts survive root/account deletion; no mutable-owner FK.
CREATE TRIGGER trg_general_work_event_immutable BEFORE UPDATE ON general_ticket_work_events
    FOR EACH ROW EXECUTE FUNCTION prevent_scoped_ai_event_change();

-- Metadata only: no legacy owner/grant classification or automatic assignment.
ALTER TABLE tickets ADD COLUMN responsibility_revision BIGINT NOT NULL DEFAULT 0 CHECK(responsibility_revision>=0);

CREATE FUNCTION preserve_responsibility_revision() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.responsibility_revision IS DISTINCT FROM OLD.responsibility_revision
       AND NOT(pg_trigger_depth()>1 AND NEW.responsibility_revision=OLD.responsibility_revision+1) THEN
        RAISE EXCEPTION 'Responsibility revision is maintained by assignment writes';
    END IF;
    RETURN NEW;
END; $$;
CREATE TRIGGER preserve_responsibility_revision BEFORE UPDATE OF responsibility_revision ON tickets
    FOR EACH ROW EXECUTE FUNCTION preserve_responsibility_revision();

CREATE FUNCTION advance_responsibility_revision() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='UPDATE' AND ROW(NEW.ticket_id,NEW.responsible,NEW.assigned_by,NEW.assigned_at)
        IS NOT DISTINCT FROM ROW(OLD.ticket_id,OLD.responsible,OLD.assigned_by,OLD.assigned_at) THEN RETURN NULL; END IF;
    IF TG_OP='INSERT' THEN
        UPDATE tickets SET responsibility_revision=responsibility_revision+1 WHERE ticket_id=NEW.ticket_id;
    ELSIF TG_OP='DELETE' THEN
        UPDATE tickets SET responsibility_revision=responsibility_revision+1 WHERE ticket_id=OLD.ticket_id;
    ELSE
        UPDATE tickets SET responsibility_revision=responsibility_revision+1 WHERE ticket_id IN(OLD.ticket_id,NEW.ticket_id);
    END IF;
    RETURN NULL;
END; $$;
CREATE TRIGGER advance_responsibility_revision AFTER INSERT OR UPDATE OR DELETE ON ticket_responsibles
    FOR EACH ROW EXECUTE FUNCTION advance_responsibility_revision();

CREATE TABLE scoped_sla_assignment_events(
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ticket_user_id BIGINT NOT NULL,
    ticket_id TEXT NOT NULL,
    ticket_public_id TEXT NOT NULL CHECK(ticket_public_id ~ '^[a-f0-9]{32}$'),
    business_id BIGINT,
    scope_kind TEXT NOT NULL,
    revision BIGINT NOT NULL CHECK(revision>0),
    previous_responsible TEXT,
    assignee TEXT NOT NULL,
    actor TEXT NOT NULL,
    source TEXT NOT NULL,
    route TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK((scope_kind='BUSINESS' AND business_id IS NOT NULL AND business_id>0) OR (scope_kind='UNRESOLVED' AND business_id IS NULL)),
    UNIQUE(ticket_user_id,ticket_id,ticket_public_id,revision)
);
CREATE FUNCTION preserve_scoped_sla_assignment_event() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Scoped SLA assignment events are append-only'; END; $$;
CREATE TRIGGER preserve_scoped_sla_assignment_event BEFORE UPDATE OR DELETE ON scoped_sla_assignment_events
    FOR EACH ROW EXECUTE FUNCTION preserve_scoped_sla_assignment_event();

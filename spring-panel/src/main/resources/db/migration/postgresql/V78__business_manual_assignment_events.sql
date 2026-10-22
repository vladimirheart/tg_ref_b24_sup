-- New human assignment provenance only; no legacy attribution or assignment backfill.
CREATE TABLE business_ticket_assignment_events (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ticket_user_id BIGINT NOT NULL,
    ticket_id TEXT NOT NULL,
    ticket_public_id TEXT NOT NULL CHECK(ticket_public_id ~ '^[a-f0-9]{32}$'),
    business_id BIGINT NOT NULL CHECK(business_id>0),
    revision BIGINT NOT NULL CHECK(revision>0),
    previous_responsible TEXT,
    assignee_user_id BIGINT NOT NULL,
    assignee TEXT NOT NULL,
    actor_user_id BIGINT NOT NULL,
    actor TEXT NOT NULL,
    action TEXT NOT NULL CHECK(action IN ('take','assign')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(ticket_user_id,ticket_id,ticket_public_id,business_id,revision)
);
CREATE FUNCTION preserve_business_assignment_event() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Business assignment events are append-only'; END; $$;
CREATE TRIGGER preserve_business_assignment_event BEFORE UPDATE OR DELETE ON business_ticket_assignment_events
    FOR EACH ROW EXECUTE FUNCTION preserve_business_assignment_event();

-- New explicit GENERAL roster only. No legacy attribution, grants or backfill.
CREATE TABLE general_ticket_participants (
    ticket_user_id BIGINT NOT NULL,
    ticket_id TEXT NOT NULL,
    ticket_public_id TEXT NOT NULL CHECK(ticket_public_id ~ '^[a-f0-9]{32}$'),
    member_user_id BIGINT NOT NULL CHECK(member_user_id>0),
    member TEXT NOT NULL,
    actor_user_id BIGINT NOT NULL,
    actor TEXT NOT NULL,
    revision BIGINT NOT NULL CHECK(revision>0),
    owner_revision BIGINT NOT NULL CHECK(owner_revision>=0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY(ticket_user_id,ticket_id,ticket_public_id,member_user_id)
);
CREATE TABLE general_ticket_participant_events (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ticket_user_id BIGINT NOT NULL,
    ticket_id TEXT NOT NULL,
    ticket_public_id TEXT NOT NULL CHECK(ticket_public_id ~ '^[a-f0-9]{32}$'),
    revision BIGINT NOT NULL CHECK(revision>0),
    owner_revision BIGINT NOT NULL CHECK(owner_revision>=0),
    member_user_id BIGINT NOT NULL CHECK(member_user_id>0),
    member TEXT NOT NULL,
    actor_user_id BIGINT NOT NULL,
    actor TEXT NOT NULL,
    action TEXT NOT NULL CHECK(action IN ('add','remove')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(ticket_user_id,ticket_id,ticket_public_id,revision)
);
CREATE FUNCTION preserve_general_participant_identity() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'General participant identities and events are immutable'; END; $$;
CREATE TRIGGER preserve_general_participant_identity BEFORE UPDATE ON general_ticket_participants
    FOR EACH ROW EXECUTE FUNCTION preserve_general_participant_identity();
CREATE TRIGGER preserve_general_participant_event BEFORE UPDATE OR DELETE ON general_ticket_participant_events
    FOR EACH ROW EXECUTE FUNCTION preserve_general_participant_identity();

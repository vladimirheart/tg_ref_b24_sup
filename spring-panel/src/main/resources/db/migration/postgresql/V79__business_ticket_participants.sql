-- Explicit new BUSINESS membership only. Never infer ownership from legacy ticket_participants.
CREATE TABLE business_ticket_participants (
    ticket_user_id BIGINT NOT NULL,
    ticket_id TEXT NOT NULL,
    ticket_public_id TEXT NOT NULL CHECK(ticket_public_id ~ '^[a-f0-9]{32}$'),
    business_id BIGINT NOT NULL CHECK(business_id>0),
    member_user_id BIGINT NOT NULL CHECK(member_user_id>0),
    member TEXT NOT NULL,
    actor_user_id BIGINT NOT NULL,
    actor TEXT NOT NULL,
    revision BIGINT NOT NULL CHECK(revision>0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY(ticket_user_id,ticket_id,ticket_public_id,business_id,member_user_id)
);
CREATE TABLE business_ticket_participant_events (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ticket_user_id BIGINT NOT NULL,
    ticket_id TEXT NOT NULL,
    ticket_public_id TEXT NOT NULL CHECK(ticket_public_id ~ '^[a-f0-9]{32}$'),
    business_id BIGINT NOT NULL CHECK(business_id>0),
    revision BIGINT NOT NULL CHECK(revision>0),
    member_user_id BIGINT NOT NULL CHECK(member_user_id>0),
    member TEXT NOT NULL,
    actor_user_id BIGINT NOT NULL,
    actor TEXT NOT NULL,
    action TEXT NOT NULL CHECK(action IN ('add','remove')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(ticket_user_id,ticket_id,ticket_public_id,business_id,revision)
);
CREATE FUNCTION preserve_business_participant_identity() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Business participant identities are immutable'; END; $$;
CREATE TRIGGER preserve_business_participant_identity BEFORE UPDATE ON business_ticket_participants
    FOR EACH ROW EXECUTE FUNCTION preserve_business_participant_identity();
CREATE TRIGGER preserve_business_participant_event BEFORE UPDATE OR DELETE ON business_ticket_participant_events
    FOR EACH ROW EXECUTE FUNCTION preserve_business_participant_identity();

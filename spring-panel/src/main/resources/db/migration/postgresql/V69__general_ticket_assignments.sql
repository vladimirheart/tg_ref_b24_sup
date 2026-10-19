-- New GENERAL assignments only. Never infer ownership from legacy ticket_responsibles.
CREATE TABLE general_ticket_assignment_events (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ticket_user_id BIGINT NOT NULL CHECK (ticket_user_id <> 0),
    ticket_id TEXT NOT NULL CHECK (btrim(ticket_id) <> ''),
    ticket_public_id VARCHAR(32) NOT NULL CHECK (ticket_public_id ~ '^[0-9a-f]{32}$'),
    revision BIGINT NOT NULL CHECK (revision > 0),
    owner_revision BIGINT NOT NULL CHECK (owner_revision >= 0),
    assignee_user_id BIGINT,
    assignee TEXT,
    actor_user_id BIGINT NOT NULL CHECK (actor_user_id > 0),
    actor TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK ((assignee_user_id IS NULL AND assignee IS NULL)
        OR (assignee_user_id IS NOT NULL AND assignee_user_id > 0 AND assignee IS NOT NULL AND btrim(assignee) <> '')),
    UNIQUE(ticket_user_id,ticket_id,ticket_public_id,revision)
);
CREATE INDEX general_assignment_history ON general_ticket_assignment_events(ticket_user_id,ticket_id,ticket_public_id,revision DESC);
CREATE TRIGGER general_assignment_immutable BEFORE UPDATE ON general_ticket_assignment_events
    FOR EACH ROW EXECUTE FUNCTION prevent_scoped_ai_event_change();

-- Explicit operator selection for a ticket that arrived without a business.
-- Existing BUSINESS and explicit GENERAL scopes are deliberately immutable here.
CREATE TABLE business_ticket_selection_events (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ticket_user_id BIGINT NOT NULL,
    ticket_id TEXT NOT NULL CHECK (btrim(ticket_id) <> ''),
    ticket_public_id TEXT NOT NULL CHECK (ticket_public_id ~ '^[a-f0-9]{32}$'),
    business_id BIGINT NOT NULL REFERENCES businesses(id) ON DELETE RESTRICT,
    actor_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    actor TEXT NOT NULL CHECK (btrim(actor) <> ''),
    source TEXT NOT NULL CHECK (source = 'OPERATOR_DIALOG'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (ticket_user_id, ticket_id, ticket_public_id)
);

CREATE FUNCTION preserve_business_ticket_selection_event() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Business ticket selection events are append-only';
END;
$$;

CREATE TRIGGER preserve_business_ticket_selection_event
    BEFORE UPDATE OR DELETE ON business_ticket_selection_events
    FOR EACH ROW EXECUTE FUNCTION preserve_business_ticket_selection_event();

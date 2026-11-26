-- Operator-only records deliberately live outside chat_history and delivery queues.
CREATE TABLE dialog_internal_messages (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ticket_user_id BIGINT NOT NULL,
    ticket_id TEXT NOT NULL,
    ticket_public_id TEXT NOT NULL CHECK (ticket_public_id ~ '^[a-f0-9]{32}$'),
    author_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    author TEXT NOT NULL CHECK (btrim(author) <> ''),
    request_id UUID NOT NULL,
    body TEXT NOT NULL CHECK (char_length(body) BETWEEN 1 AND 10000 AND btrim(body) <> ''),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (ticket_user_id,ticket_id) REFERENCES tickets(user_id,ticket_id) ON DELETE RESTRICT,
    UNIQUE(author_user_id,request_id)
);
CREATE INDEX dialog_internal_messages_owner ON dialog_internal_messages(ticket_public_id,id);

CREATE TABLE dialog_business_transfer_events (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ticket_user_id BIGINT NOT NULL,
    ticket_id TEXT NOT NULL,
    ticket_public_id TEXT NOT NULL CHECK (ticket_public_id ~ '^[a-f0-9]{32}$'),
    previous_business_id BIGINT NOT NULL REFERENCES businesses(id) ON DELETE RESTRICT,
    business_id BIGINT NOT NULL REFERENCES businesses(id) ON DELETE RESTRICT,
    actor_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    actor TEXT NOT NULL,
    previous_responsible TEXT,
    revision BIGINT NOT NULL CHECK(revision > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK(previous_business_id <> business_id),
    UNIQUE(ticket_user_id,ticket_id,ticket_public_id,revision)
);
CREATE FUNCTION preserve_dialog_operator_record() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Operator records are append-only'; END; $$;
CREATE TRIGGER preserve_dialog_internal_message BEFORE UPDATE OR DELETE ON dialog_internal_messages
    FOR EACH ROW EXECUTE FUNCTION preserve_dialog_operator_record();
CREATE TRIGGER preserve_dialog_business_transfer BEFORE UPDATE OR DELETE ON dialog_business_transfer_events
    FOR EACH ROW EXECUTE FUNCTION preserve_dialog_operator_record();

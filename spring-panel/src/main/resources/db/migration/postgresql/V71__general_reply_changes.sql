-- New GENERAL commands only. Existing replies/history are not reclassified.
CREATE TABLE general_ticket_reply_changes (
    request_key VARCHAR(32) PRIMARY KEY CHECK (request_key ~ '^[0-9a-f]{32}$'),
    source_request_key VARCHAR(32) NOT NULL CHECK (source_request_key ~ '^[0-9a-f]{32}$'),
    ticket_user_id BIGINT NOT NULL,
    ticket_id TEXT NOT NULL,
    ticket_public_id VARCHAR(32) NOT NULL CHECK (ticket_public_id ~ '^[0-9a-f]{32}$'),
    owner_revision BIGINT NOT NULL CHECK (owner_revision >= 0),
    actor_user_id BIGINT NOT NULL,
    actor TEXT NOT NULL,
    channel_id BIGINT NOT NULL,
    destination_sha256 VARCHAR(64) NOT NULL CHECK (destination_sha256 ~ '^[0-9a-f]{64}$'),
    chat_history_id BIGINT NOT NULL,
    provider_message_id BIGINT NOT NULL CHECK (provider_message_id > 0),
    expected_sha256 VARCHAR(64) NOT NULL CHECK (expected_sha256 ~ '^[0-9a-f]{64}$'),
    action VARCHAR(8) NOT NULL CHECK (action IN ('EDIT','DELETE')),
    message TEXT NOT NULL,
    state VARCHAR(16) NOT NULL DEFAULT 'QUEUED' CHECK (state IN ('QUEUED','SENDING','APPLIED','CANCELLED','UNKNOWN')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK ((action='DELETE' AND message='') OR (action='EDIT' AND length(message) BETWEEN 1 AND 4000))
);
-- An ambiguous attempt blocks later commands for the same provider message.
CREATE UNIQUE INDEX idx_general_reply_change_inflight ON general_ticket_reply_changes(source_request_key)
    WHERE state IN ('QUEUED','SENDING','UNKNOWN');
CREATE INDEX idx_general_reply_change_owner ON general_ticket_reply_changes(ticket_public_id,created_at DESC);

CREATE FUNCTION protect_general_reply_change() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF ROW(NEW.request_key,NEW.source_request_key,NEW.ticket_user_id,NEW.ticket_id,NEW.ticket_public_id,
           NEW.owner_revision,NEW.actor_user_id,NEW.actor,NEW.channel_id,NEW.destination_sha256,
           NEW.chat_history_id,NEW.provider_message_id,NEW.expected_sha256,NEW.action,NEW.message,NEW.created_at)
       IS DISTINCT FROM ROW(OLD.request_key,OLD.source_request_key,OLD.ticket_user_id,OLD.ticket_id,OLD.ticket_public_id,
           OLD.owner_revision,OLD.actor_user_id,OLD.actor,OLD.channel_id,OLD.destination_sha256,
           OLD.chat_history_id,OLD.provider_message_id,OLD.expected_sha256,OLD.action,OLD.message,OLD.created_at)
       OR NOT ((OLD.state='QUEUED' AND NEW.state='SENDING')
            OR (OLD.state='SENDING' AND NEW.state IN ('APPLIED','CANCELLED','UNKNOWN'))) THEN
        RAISE EXCEPTION 'GENERAL reply change snapshot and terminal result are immutable' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_general_reply_change BEFORE UPDATE ON general_ticket_reply_changes
    FOR EACH ROW EXECUTE FUNCTION protect_general_reply_change();
CREATE TRIGGER trg_general_reply_change_delete BEFORE DELETE ON general_ticket_reply_changes
    FOR EACH ROW EXECUTE FUNCTION prevent_scoped_ai_event_change();

CREATE TABLE general_ticket_reply_change_events (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    request_key VARCHAR(32) NOT NULL,
    state VARCHAR(16) NOT NULL CHECK (state IN ('QUEUED','SENDING','APPLIED','CANCELLED','UNKNOWN')),
    actor_user_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(request_key,state)
);
CREATE TRIGGER trg_general_reply_change_event_immutable BEFORE UPDATE OR DELETE ON general_ticket_reply_change_events
    FOR EACH ROW EXECUTE FUNCTION prevent_scoped_ai_event_change();

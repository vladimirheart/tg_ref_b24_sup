-- Durable bounded upload intents. No historical media are classified or copied.
CREATE TABLE general_ticket_document_replies (
    request_key VARCHAR(32) PRIMARY KEY CHECK (request_key ~ '^[0-9a-f]{32}$'),
    ticket_user_id BIGINT NOT NULL,
    ticket_id TEXT NOT NULL,
    ticket_public_id VARCHAR(32) NOT NULL CHECK (ticket_public_id ~ '^[0-9a-f]{32}$'),
    owner_revision BIGINT NOT NULL CHECK (owner_revision>=0),
    actor_user_id BIGINT NOT NULL,
    actor TEXT NOT NULL,
    channel_id BIGINT NOT NULL,
    destination_sha256 VARCHAR(64) NOT NULL CHECK (destination_sha256 ~ '^[0-9a-f]{64}$'),
    original_name VARCHAR(200) NOT NULL CHECK (btrim(original_name)<>'' AND position('/' in original_name)=0 AND position(chr(92) in original_name)=0),
    caption TEXT NOT NULL CHECK (length(caption)<=1024),
    payload BYTEA NOT NULL CHECK (octet_length(payload) BETWEEN 1 AND 10485760),
    content_sha256 VARCHAR(64) NOT NULL CHECK (content_sha256=encode(sha256(payload),'hex')),
    storage_key TEXT NOT NULL UNIQUE CHECK (storage_key=ticket_public_id||'/general-'||request_key||'.bin'),
    state VARCHAR(16) NOT NULL DEFAULT 'QUEUED' CHECK (state IN ('QUEUED','SENDING','SENT','CANCELLED','UNKNOWN')),
    chat_history_id BIGINT UNIQUE,
    provider_message_id BIGINT CHECK (provider_message_id>0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK ((state='SENT')=(chat_history_id IS NOT NULL)),
    CHECK ((state='SENT')=(provider_message_id IS NOT NULL))
);
CREATE INDEX idx_general_document_owner ON general_ticket_document_replies(ticket_public_id,created_at DESC);
CREATE FUNCTION protect_general_document_reply() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF ROW(NEW.request_key,NEW.ticket_user_id,NEW.ticket_id,NEW.ticket_public_id,NEW.owner_revision,
           NEW.actor_user_id,NEW.actor,NEW.channel_id,NEW.destination_sha256,NEW.original_name,NEW.caption,
           NEW.payload,NEW.content_sha256,NEW.storage_key,NEW.created_at)
       IS DISTINCT FROM ROW(OLD.request_key,OLD.ticket_user_id,OLD.ticket_id,OLD.ticket_public_id,OLD.owner_revision,
           OLD.actor_user_id,OLD.actor,OLD.channel_id,OLD.destination_sha256,OLD.original_name,OLD.caption,
           OLD.payload,OLD.content_sha256,OLD.storage_key,OLD.created_at)
       OR NOT ((OLD.state='QUEUED' AND NEW.state='SENDING')
            OR (OLD.state='SENDING' AND NEW.state IN ('SENT','CANCELLED','UNKNOWN'))) THEN
        RAISE EXCEPTION 'GENERAL document intent and terminal result are immutable' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_general_document_reply BEFORE UPDATE ON general_ticket_document_replies
    FOR EACH ROW EXECUTE FUNCTION protect_general_document_reply();
CREATE TRIGGER trg_general_document_reply_delete BEFORE DELETE ON general_ticket_document_replies
    FOR EACH ROW EXECUTE FUNCTION prevent_scoped_ai_event_change();
CREATE TABLE general_ticket_document_events (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    request_key VARCHAR(32) NOT NULL,
    state VARCHAR(16) NOT NULL CHECK (state IN ('QUEUED','SENDING','SENT','CANCELLED','UNKNOWN')),
    actor_user_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(request_key,state)
);
CREATE TRIGGER trg_general_document_event_immutable BEFORE UPDATE OR DELETE ON general_ticket_document_events
    FOR EACH ROW EXECUTE FUNCTION prevent_scoped_ai_event_change();

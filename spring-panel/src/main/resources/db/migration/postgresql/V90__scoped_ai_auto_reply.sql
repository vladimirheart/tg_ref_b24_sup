-- No legacy import, seeded approvals or automatic enablement.
ALTER TABLE ui_event_outbox ADD COLUMN incoming_history_id BIGINT;
CREATE FUNCTION protect_ui_incoming_history_link() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.incoming_history_id IS DISTINCT FROM OLD.incoming_history_id THEN
        RAISE EXCEPTION 'UI incoming history link is immutable' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_ui_incoming_history_link BEFORE UPDATE ON ui_event_outbox
    FOR EACH ROW EXECUTE FUNCTION protect_ui_incoming_history_link();

CREATE TABLE ai_scoped_reply_rules (
    rule_key VARCHAR(36) PRIMARY KEY,
    business_id BIGINT NOT NULL REFERENCES businesses(id),
    trigger_text TEXT NOT NULL CHECK (length(trigger_text) BETWEEN 1 AND 500 AND trigger_text=btrim(trigger_text)),
    source_id BIGINT NOT NULL,
    source_revision BIGINT NOT NULL CHECK (source_revision>0),
    revision BIGINT NOT NULL DEFAULT 1 CHECK (revision>0),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    reviewed_by_user_id BIGINT NOT NULL,
    reviewed_by TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX uq_ai_reply_active_trigger ON ai_scoped_reply_rules(business_id,trigger_text) WHERE active;
CREATE INDEX idx_ai_reply_source ON ai_scoped_reply_rules(business_id,source_id,created_at DESC);
CREATE FUNCTION protect_ai_reply_rule() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF ROW(NEW.rule_key,NEW.business_id,NEW.trigger_text,NEW.source_id,NEW.source_revision,
           NEW.reviewed_by_user_id,NEW.reviewed_by,NEW.created_at)
       IS DISTINCT FROM ROW(OLD.rule_key,OLD.business_id,OLD.trigger_text,OLD.source_id,OLD.source_revision,
           OLD.reviewed_by_user_id,OLD.reviewed_by,OLD.created_at)
       OR NOT (OLD.active AND NOT NEW.active AND NEW.revision=OLD.revision+1) THEN
        RAISE EXCEPTION 'AI rule identity and review are immutable; only revocation is allowed' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_ai_reply_rule BEFORE UPDATE ON ai_scoped_reply_rules
    FOR EACH ROW EXECUTE FUNCTION protect_ai_reply_rule();
CREATE TABLE ai_scoped_reply_rule_events (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    rule_key VARCHAR(36) NOT NULL,
    business_id BIGINT NOT NULL,
    revision BIGINT NOT NULL,
    action TEXT NOT NULL CHECK (action IN ('reviewed','revoked')),
    actor_user_id BIGINT NOT NULL,
    actor TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(rule_key,revision)
);
CREATE TRIGGER trg_ai_reply_rule_event BEFORE UPDATE ON ai_scoped_reply_rule_events
    FOR EACH ROW EXECUTE FUNCTION prevent_scoped_ai_event_change();

-- Payload stays in the reviewed source and chat history; this is a frozen delivery proof.
-- No FK to ephemeral UI events, sources or tickets: terminal receipts must survive their deletion.
CREATE TABLE ai_scoped_text_replies (
    incoming_event_id BIGINT PRIMARY KEY CHECK (incoming_event_id>0),
    ticket_user_id BIGINT NOT NULL,
    ticket_id TEXT NOT NULL,
    ticket_public_id VARCHAR(32) NOT NULL CHECK (ticket_public_id ~ '^[a-f0-9]{32}$'),
    business_id BIGINT NOT NULL,
    channel_id BIGINT NOT NULL,
    destination_sha256 VARCHAR(64) NOT NULL CHECK (destination_sha256 ~ '^[a-f0-9]{64}$'),
    input_history_id BIGINT NOT NULL,
    owner_revision BIGINT NOT NULL CHECK (owner_revision>=0),
    control_stamp TEXT NOT NULL,
    rule_key VARCHAR(36) NOT NULL,
    rule_revision BIGINT NOT NULL,
    source_id BIGINT NOT NULL,
    source_revision BIGINT NOT NULL,
    response_sha256 VARCHAR(64) NOT NULL CHECK (response_sha256 ~ '^[a-f0-9]{64}$'),
    state TEXT NOT NULL DEFAULT 'QUEUED' CHECK (state IN ('QUEUED','SENDING','SENT','CANCELLED','UNKNOWN')),
    chat_history_id BIGINT UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(ticket_public_id,input_history_id),
    CHECK ((state='SENT')=(chat_history_id IS NOT NULL))
);
CREATE INDEX idx_ai_text_reply_owner ON ai_scoped_text_replies(business_id,ticket_public_id,updated_at DESC);
CREATE FUNCTION protect_ai_text_reply() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF (to_jsonb(NEW)-ARRAY['state','chat_history_id','updated_at'])
       IS DISTINCT FROM (to_jsonb(OLD)-ARRAY['state','chat_history_id','updated_at'])
       OR NOT ((OLD.state='QUEUED' AND NEW.state='SENDING')
          OR (OLD.state='SENDING' AND NEW.state IN ('SENT','CANCELLED','UNKNOWN'))) THEN
        RAISE EXCEPTION 'AI delivery proof and terminal result are immutable' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_ai_text_reply BEFORE UPDATE ON ai_scoped_text_replies
    FOR EACH ROW EXECUTE FUNCTION protect_ai_text_reply();

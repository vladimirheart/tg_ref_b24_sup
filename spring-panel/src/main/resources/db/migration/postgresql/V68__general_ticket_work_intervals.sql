-- Observe only new manual GENERAL work. No attribution/backfill of legacy activity or spans.
CREATE TABLE general_ticket_work_intervals (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ticket_user_id BIGINT NOT NULL CHECK (ticket_user_id <> 0),
    ticket_id TEXT NOT NULL CHECK (btrim(ticket_id) <> ''),
    ticket_public_id VARCHAR(32) NOT NULL CHECK (ticket_public_id ~ '^[0-9a-f]{32}$'),
    opened_revision BIGINT NOT NULL CHECK (opened_revision > 0),
    started_at TIMESTAMPTZ NOT NULL,
    started_by_user_id BIGINT NOT NULL CHECK (started_by_user_id > 0),
    started_by TEXT NOT NULL,
    outcome TEXT NOT NULL DEFAULT 'OPEN',
    ended_revision BIGINT,
    ended_at TIMESTAMPTZ,
    ended_by_user_id BIGINT,
    ended_by TEXT,
    invalid_reason TEXT,
    CONSTRAINT general_interval_outcome CHECK (outcome IN ('OPEN','COMPLETED','INVALIDATED')),
    CONSTRAINT general_interval_end CHECK (
        (outcome='OPEN' AND ended_revision IS NULL AND ended_at IS NULL AND ended_by_user_id IS NULL AND ended_by IS NULL AND invalid_reason IS NULL)
        OR (outcome IN ('COMPLETED','INVALIDATED') AND ended_revision IS NOT NULL AND ended_revision>opened_revision
            AND ended_at IS NOT NULL AND ended_at>=started_at AND ended_by_user_id IS NOT NULL AND ended_by_user_id>0 AND ended_by IS NOT NULL
            AND ((outcome='COMPLETED' AND invalid_reason IS NULL)
                OR (outcome='INVALIDATED' AND invalid_reason IS NOT NULL AND invalid_reason IN ('STATE_CHANGED','CLOCK_REGRESSION'))))
    ),
    UNIQUE(ticket_user_id,ticket_id,ticket_public_id,opened_revision)
);
CREATE UNIQUE INDEX general_interval_one_open ON general_ticket_work_intervals(ticket_user_id,ticket_id,ticket_public_id) WHERE outcome='OPEN';
CREATE INDEX general_interval_history ON general_ticket_work_intervals(ticket_user_id,ticket_id,ticket_public_id,id DESC);

CREATE FUNCTION protect_general_work_interval() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF ROW(NEW.id,NEW.ticket_user_id,NEW.ticket_id,NEW.ticket_public_id,NEW.opened_revision,NEW.started_at,NEW.started_by_user_id,NEW.started_by)
        IS DISTINCT FROM ROW(OLD.id,OLD.ticket_user_id,OLD.ticket_id,OLD.ticket_public_id,OLD.opened_revision,OLD.started_at,OLD.started_by_user_id,OLD.started_by)
        OR OLD.outcome<>'OPEN' OR NEW.outcome NOT IN ('COMPLETED','INVALIDATED') THEN
        RAISE EXCEPTION 'GENERAL interval identity and terminal result are immutable' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER general_interval_immutable BEFORE UPDATE ON general_ticket_work_intervals
    FOR EACH ROW EXECUTE FUNCTION protect_general_work_interval();

-- Called from the authorized root UPDATE's RETURNING row in the same statement as audit/outbox.
-- Sequential SQL here closes an old open interval before the partial-unique INSERT of a new one.
CREATE FUNCTION record_general_work_interval(p_user BIGINT,p_ticket TEXT,p_public TEXT,p_from TEXT,p_to TEXT,
    p_old_revision BIGINT,p_new_revision BIGINT,p_actor BIGINT,p_name TEXT,p_at TIMESTAMPTZ)
RETURNS BOOLEAN LANGUAGE plpgsql AS $$
BEGIN
    IF p_user IS NULL OR p_user=0 OR p_ticket IS NULL OR btrim(p_ticket)='' OR p_public IS NULL OR p_public !~ '^[0-9a-f]{32}$'
        OR p_actor IS NULL OR p_actor<1 OR p_name IS NULL OR p_at IS NULL
        OR p_old_revision IS NULL OR p_old_revision<0 OR p_new_revision IS NULL OR p_new_revision<>p_old_revision+1
        OR p_from IS NULL OR p_to IS NULL OR NOT general_ticket_transition_allowed(p_from,p_to) THEN
        RAISE EXCEPTION 'Invalid GENERAL interval transition' USING ERRCODE='23514';
    END IF;
    UPDATE general_ticket_work_intervals span SET
        outcome=CASE WHEN p_from='open' AND span.opened_revision=p_old_revision AND p_at>=span.started_at THEN 'COMPLETED' ELSE 'INVALIDATED' END,
        ended_revision=p_new_revision,ended_at=GREATEST(p_at,span.started_at),ended_by_user_id=p_actor,ended_by=p_name,
        invalid_reason=CASE WHEN p_at<span.started_at THEN 'CLOCK_REGRESSION'
            WHEN p_from<>'open' OR span.opened_revision<>p_old_revision THEN 'STATE_CHANGED' ELSE NULL END
     WHERE span.ticket_user_id=p_user AND span.ticket_id=p_ticket AND span.ticket_public_id=p_public AND span.outcome='OPEN';
    IF p_to='open' THEN
        INSERT INTO general_ticket_work_intervals(ticket_user_id,ticket_id,ticket_public_id,opened_revision,started_at,started_by_user_id,started_by)
        VALUES(p_user,p_ticket,p_public,p_new_revision,p_at,p_actor,p_name);
    END IF;
    RETURN TRUE;
END;
$$;

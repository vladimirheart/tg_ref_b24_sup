-- Capture provenance for NEW requests only. Historical pending rows stay unbound.
-- This is schema preparation: consumers must still compare the saved epoch/tuple.
-- A random epoch is not a work/status revision or recipient authorization.
ALTER TABLE tickets ADD COLUMN feedback_context_epoch UUID NOT NULL DEFAULT gen_random_uuid();

CREATE FUNCTION maintain_feedback_context_epoch() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        -- Even an explicitly supplied token cannot resurrect a deleted incarnation.
        NEW.feedback_context_epoch := gen_random_uuid();
    ELSE
        IF NEW.feedback_context_epoch IS DISTINCT FROM OLD.feedback_context_epoch THEN
            RAISE EXCEPTION 'Feedback context epoch is maintained by PostgreSQL' USING ERRCODE = '23514';
        END IF;
        IF ROW(NEW.user_id, NEW.ticket_id, NEW.channel_id, NEW.ticket_public_id, NEW.business_id, NEW.is_general)
           IS DISTINCT FROM
           ROW(OLD.user_id, OLD.ticket_id, OLD.channel_id, OLD.ticket_public_id, OLD.business_id, OLD.is_general) THEN
            NEW.feedback_context_epoch := gen_random_uuid();
        END IF;
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_feedback_context_epoch BEFORE INSERT OR UPDATE ON tickets
    FOR EACH ROW EXECUTE FUNCTION maintain_feedback_context_epoch();

ALTER TABLE pending_feedback_requests
    ADD COLUMN ticket_context_epoch UUID,
    ADD COLUMN ticket_public_id VARCHAR(32),
    ADD COLUMN ticket_business_id BIGINT,
    ADD COLUMN ticket_is_general BOOLEAN,
    ADD CONSTRAINT ck_pending_feedback_context CHECK (
        (ticket_context_epoch IS NULL AND ticket_public_id IS NULL
            AND ticket_business_id IS NULL AND ticket_is_general IS NULL)
        OR (ticket_context_epoch IS NOT NULL AND ticket_is_general IS NOT NULL
            AND (ticket_public_id IS NULL OR ticket_public_id ~ '^[0-9a-f]{32}$')
            AND (NOT ticket_is_general OR ticket_business_id IS NULL))
    );

CREATE FUNCTION capture_pending_feedback_context() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE
    owner tickets%ROWTYPE;
BEGIN
    IF TG_OP = 'UPDATE' THEN
        -- Expiry/source/sent_at can change; neither legacy nor captured identity can be rebound.
        IF ROW(NEW.user_id, NEW.channel_id, NEW.ticket_id, NEW.created_at,
               NEW.ticket_context_epoch, NEW.ticket_public_id, NEW.ticket_business_id, NEW.ticket_is_general)
           IS DISTINCT FROM
           ROW(OLD.user_id, OLD.channel_id, OLD.ticket_id, OLD.created_at,
               OLD.ticket_context_epoch, OLD.ticket_public_id, OLD.ticket_business_id, OLD.ticket_is_general) THEN
            RAISE EXCEPTION 'Pending feedback provenance is immutable' USING ERRCODE = '23514';
        END IF;
        RETURN NEW;
    END IF;

    BEGIN
        SELECT * INTO STRICT owner FROM tickets WHERE ticket_id = NEW.ticket_id FOR SHARE;
    EXCEPTION WHEN NO_DATA_FOUND OR TOO_MANY_ROWS THEN
        RAISE EXCEPTION 'Pending feedback requires one exact ticket owner' USING ERRCODE = '23514';
    END;
    IF NEW.user_id IS DISTINCT FROM owner.user_id OR NEW.channel_id IS DISTINCT FROM owner.channel_id THEN
        RAISE EXCEPTION 'Pending feedback owner does not match ticket' USING ERRCODE = '23514';
    END IF;
    IF NEW.ticket_context_epoch IS NULL THEN
        IF NEW.ticket_public_id IS NOT NULL OR NEW.ticket_business_id IS NOT NULL OR NEW.ticket_is_general IS NOT NULL THEN
            RAISE EXCEPTION 'Partial pending feedback provenance' USING ERRCODE = '23514';
        END IF;
    ELSIF ROW(NEW.ticket_context_epoch, NEW.ticket_public_id, NEW.ticket_business_id, NEW.ticket_is_general)
          IS DISTINCT FROM
          ROW(owner.feedback_context_epoch, owner.ticket_public_id, owner.business_id, owner.is_general) THEN
        RAISE EXCEPTION 'Pending feedback provenance does not match ticket' USING ERRCODE = '23514';
    END IF;
    NEW.ticket_context_epoch := owner.feedback_context_epoch;
    NEW.ticket_public_id := owner.ticket_public_id;
    NEW.ticket_business_id := owner.business_id;
    NEW.ticket_is_general := owner.is_general;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_pending_feedback_context BEFORE INSERT OR UPDATE ON pending_feedback_requests
    FOR EACH ROW EXECUTE FUNCTION capture_pending_feedback_context();

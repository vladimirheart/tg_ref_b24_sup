-- New BUSINESS manual work only. No classification or import of legacy categories.
CREATE FUNCTION valid_business_ticket_categories(items JSONB) RETURNS BOOLEAN LANGUAGE SQL IMMUTABLE AS $$
    SELECT CASE WHEN jsonb_typeof(items) <> 'array' THEN FALSE ELSE
        jsonb_array_length(items) <= 50
        AND NOT EXISTS (
            SELECT 1 FROM jsonb_array_elements(items) item
             WHERE jsonb_typeof(item) <> 'string'
                OR char_length(item #>> '{}') NOT BETWEEN 1 AND 160
                OR item #>> '{}' <> btrim(item #>> '{}')
                OR item #>> '{}' ~ '[[:cntrl:]]')
        AND (SELECT count(DISTINCT item) FROM jsonb_array_elements(items) item) = jsonb_array_length(items)
    END
$$;

ALTER TABLE tickets ADD COLUMN business_work_revision BIGINT NOT NULL DEFAULT 0 CHECK (business_work_revision >= 0);
ALTER TABLE tickets ADD COLUMN business_categories JSONB NOT NULL DEFAULT '[]' CHECK (valid_business_ticket_categories(business_categories));

CREATE FUNCTION advance_business_work_revision() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.business_work_revision IS DISTINCT FROM OLD.business_work_revision THEN
        RAISE EXCEPTION 'Business work revision is maintained by root writes';
    END IF;
    -- A different full owner starts with no inherited category projection.
    -- Workflow-written values remain in immutable work events of their original owner.
    IF ROW(NEW.user_id,NEW.ticket_id,NEW.ticket_public_id,NEW.business_id,NEW.is_general)
       IS DISTINCT FROM ROW(OLD.user_id,OLD.ticket_id,OLD.ticket_public_id,OLD.business_id,OLD.is_general) THEN
        NEW.business_categories := '[]'::jsonb;
    END IF;
    IF (OLD.business_id IS NOT NULL OR NEW.business_id IS NOT NULL)
       AND ROW(NEW.user_id,NEW.ticket_id,NEW.ticket_public_id,NEW.business_id,NEW.is_general,NEW.channel_id,NEW.status,NEW.business_categories)
           IS DISTINCT FROM ROW(OLD.user_id,OLD.ticket_id,OLD.ticket_public_id,OLD.business_id,OLD.is_general,OLD.channel_id,OLD.status,OLD.business_categories) THEN
        NEW.business_work_revision := OLD.business_work_revision + 1;
    END IF;
    RETURN NEW;
END; $$;
CREATE TRIGGER advance_business_work_revision BEFORE UPDATE ON tickets
    FOR EACH ROW EXECUTE FUNCTION advance_business_work_revision();

CREATE TABLE business_ticket_work_events (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ticket_user_id BIGINT NOT NULL,
    ticket_id TEXT NOT NULL CHECK (btrim(ticket_id) <> ''),
    ticket_public_id TEXT NOT NULL CHECK (ticket_public_id ~ '^[a-f0-9]{32}$'),
    business_id BIGINT NOT NULL CHECK (business_id > 0),
    revision BIGINT NOT NULL CHECK (revision > 0),
    action TEXT NOT NULL CHECK (action IN ('close','reopen','categories')),
    from_status TEXT NOT NULL,
    to_status TEXT NOT NULL,
    previous_categories JSONB NOT NULL CHECK (valid_business_ticket_categories(previous_categories)),
    categories JSONB NOT NULL CHECK (valid_business_ticket_categories(categories)),
    actor_user_id BIGINT NOT NULL CHECK (actor_user_id > 0),
    actor TEXT NOT NULL CHECK (btrim(actor) <> ''),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (ticket_user_id,ticket_id,ticket_public_id,business_id,revision),
    CHECK (
        (action='close' AND from_status IN ('pending','open') AND to_status='resolved' AND jsonb_array_length(categories)>0)
        OR (action='reopen' AND from_status IN ('closed','resolved') AND to_status='pending' AND previous_categories=categories)
        OR (action='categories' AND from_status IN ('pending','open') AND to_status=from_status AND previous_categories<>categories)
    )
);
CREATE FUNCTION preserve_business_work_event() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Business work events are append-only'; END; $$;
CREATE TRIGGER preserve_business_work_event BEFORE UPDATE OR DELETE ON business_ticket_work_events
    FOR EACH ROW EXECUTE FUNCTION preserve_business_work_event();

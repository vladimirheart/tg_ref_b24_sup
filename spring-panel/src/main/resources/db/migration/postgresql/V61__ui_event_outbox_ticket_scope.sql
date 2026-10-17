-- Старые события не получают владельца из сегодняшнего состояния tickets.
ALTER TABLE ui_event_outbox
    ADD COLUMN ticket_user_id BIGINT,
    ADD COLUMN ticket_public_id VARCHAR(32),
    ADD COLUMN ticket_scope_kind VARCHAR(20),
    ADD COLUMN ticket_business_id BIGINT;

ALTER TABLE ui_event_outbox ADD CONSTRAINT chk_ui_event_outbox_ticket_scope CHECK (
    (ticket_user_id IS NULL AND ticket_public_id IS NULL
        AND ticket_scope_kind IS NULL AND ticket_business_id IS NULL)
    OR
    (ticket_user_id IS NOT NULL AND btrim(ticket_id) <> ''
        AND ticket_scope_kind IS NOT NULL
        AND (ticket_public_id IS NULL OR ticket_public_id ~ '^[0-9a-f]{32}$')
        AND ((ticket_scope_kind = 'BUSINESS' AND ticket_business_id IS NOT NULL AND ticket_business_id > 0)
            OR (ticket_scope_kind IN ('GENERAL', 'UNRESOLVED') AND ticket_business_id IS NULL)))
);

CREATE FUNCTION prevent_ui_event_outbox_ticket_scope_change()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF ROW(OLD.ticket_user_id, OLD.ticket_id, OLD.ticket_public_id, OLD.ticket_scope_kind, OLD.ticket_business_id)
       IS DISTINCT FROM
       ROW(NEW.ticket_user_id, NEW.ticket_id, NEW.ticket_public_id, NEW.ticket_scope_kind, NEW.ticket_business_id) THEN
        RAISE EXCEPTION 'UI outbox ticket scope is immutable' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_ui_event_outbox_ticket_scope_immutable
    BEFORE UPDATE OF ticket_user_id, ticket_id, ticket_public_id, ticket_scope_kind, ticket_business_id ON ui_event_outbox
    FOR EACH ROW EXECUTE FUNCTION prevent_ui_event_outbox_ticket_scope_change();

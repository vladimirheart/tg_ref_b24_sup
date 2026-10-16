-- Historical notifications stay unclassified; never infer ownership from text or URLs.
ALTER TABLE notifications
    ADD COLUMN ticket_user_id BIGINT,
    ADD COLUMN ticket_id TEXT,
    ADD COLUMN ticket_public_id VARCHAR(32),
    ADD COLUMN ticket_scope_kind VARCHAR(20),
    ADD COLUMN ticket_business_id BIGINT;

ALTER TABLE notifications ADD CONSTRAINT chk_notification_ticket_scope CHECK (
    (ticket_user_id IS NULL AND ticket_id IS NULL AND ticket_public_id IS NULL
        AND ticket_scope_kind IS NULL AND ticket_business_id IS NULL)
    OR
    (ticket_user_id IS NOT NULL AND ticket_id IS NOT NULL AND btrim(ticket_id) <> ''
        AND ticket_scope_kind IS NOT NULL
        AND (ticket_public_id IS NULL OR ticket_public_id ~ '^[0-9a-f]{32}$')
        AND ((ticket_scope_kind = 'BUSINESS' AND ticket_business_id IS NOT NULL AND ticket_business_id > 0)
            OR (ticket_scope_kind IN ('GENERAL', 'UNRESOLVED') AND ticket_business_id IS NULL)))
);

CREATE INDEX idx_notification_ticket_owner ON notifications(ticket_user_id, ticket_id)
    WHERE ticket_user_id IS NOT NULL;

CREATE FUNCTION prevent_notification_ticket_scope_change()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF ROW(OLD.ticket_user_id, OLD.ticket_id, OLD.ticket_public_id, OLD.ticket_scope_kind, OLD.ticket_business_id)
       IS DISTINCT FROM
       ROW(NEW.ticket_user_id, NEW.ticket_id, NEW.ticket_public_id, NEW.ticket_scope_kind, NEW.ticket_business_id) THEN
        RAISE EXCEPTION 'Notification ticket scope is immutable' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_notification_ticket_scope_immutable
    BEFORE UPDATE OF ticket_user_id, ticket_id, ticket_public_id, ticket_scope_kind, ticket_business_id ON notifications
    FOR EACH ROW EXECUTE FUNCTION prevent_notification_ticket_scope_change();

-- 01-278 S2A: immutable public locator for new tickets.
-- Legacy tickets deliberately retain NULL until a separately approved backfill.

ALTER TABLE tickets ADD COLUMN IF NOT EXISTS ticket_public_id VARCHAR(32);

ALTER TABLE tickets
    ADD CONSTRAINT ck_tickets_ticket_public_id_format
    CHECK (
        ticket_public_id IS NULL
        OR ticket_public_id ~ '^[0-9a-f]{32}$'
    );

CREATE UNIQUE INDEX IF NOT EXISTS ux_tickets_ticket_public_id
    ON tickets(ticket_public_id)
    WHERE ticket_public_id IS NOT NULL;

CREATE OR REPLACE FUNCTION prevent_ticket_public_id_change()
RETURNS TRIGGER AS $$
BEGIN
    IF OLD.ticket_public_id IS NOT NULL
       AND NEW.ticket_public_id IS DISTINCT FROM OLD.ticket_public_id THEN
        RAISE EXCEPTION 'ticket_public_id is immutable once assigned'
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_tickets_ticket_public_id_immutable ON tickets;
CREATE TRIGGER trg_tickets_ticket_public_id_immutable
BEFORE UPDATE OF ticket_public_id ON tickets
FOR EACH ROW
EXECUTE FUNCTION prevent_ticket_public_id_change();

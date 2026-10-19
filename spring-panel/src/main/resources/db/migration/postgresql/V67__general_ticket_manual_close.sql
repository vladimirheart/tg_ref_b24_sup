-- Expand only the manual GENERAL work path. Existing tickets/events/grants remain unchanged.
CREATE FUNCTION general_ticket_transition_allowed(from_status TEXT, to_status TEXT)
RETURNS BOOLEAN LANGUAGE SQL IMMUTABLE STRICT AS $$
    SELECT (from_status, to_status) IN (
        ('pending', 'open'), ('open', 'pending'),
        ('pending', 'closed'), ('open', 'closed'), ('closed', 'pending')
    );
$$;

ALTER TABLE general_ticket_work_events
    DROP CONSTRAINT general_ticket_work_events_from_status_check,
    DROP CONSTRAINT general_ticket_work_events_check,
    ADD CONSTRAINT general_ticket_work_transition_check
        CHECK (general_ticket_transition_allowed(from_status, to_status));

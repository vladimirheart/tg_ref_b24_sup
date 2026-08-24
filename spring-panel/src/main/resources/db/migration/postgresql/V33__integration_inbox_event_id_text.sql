ALTER TABLE integration_inbound_event_inbox
    ALTER COLUMN event_id TYPE TEXT USING event_id::text;
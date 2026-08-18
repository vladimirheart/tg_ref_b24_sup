ALTER TABLE tickets
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP WITH TIME ZONE;

UPDATE tickets AS t
   SET created_at = COALESCE(
       (
           SELECT MIN(m.created_at)
             FROM messages m
            WHERE m.ticket_id = t.ticket_id
              AND (t.channel_id IS NULL OR m.channel_id = t.channel_id)
              AND m.created_at IS NOT NULL
       ),
       (
           SELECT MIN(ch.timestamp)
             FROM chat_history ch
            WHERE ch.ticket_id = t.ticket_id
              AND (t.channel_id IS NULL OR ch.channel_id = t.channel_id)
              AND ch.timestamp IS NOT NULL
       ),
       t.resolved_at,
       CURRENT_TIMESTAMP
   )
 WHERE t.created_at IS NULL;

ALTER TABLE tickets
    ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE tickets
    ALTER COLUMN created_at SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_tickets_created_at
    ON tickets(created_at);

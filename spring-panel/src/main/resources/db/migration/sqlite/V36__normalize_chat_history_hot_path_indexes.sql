CREATE INDEX IF NOT EXISTS idx_history_ticket_order
    ON chat_history(ticket_id, substr(COALESCE(timestamp, ''), 1, 19), COALESCE(tg_message_id, 0), id);

CREATE INDEX IF NOT EXISTS idx_history_ticket_sender_order
    ON chat_history(ticket_id, lower(COALESCE(sender, '')), substr(COALESCE(timestamp, ''), 1, 19), id);

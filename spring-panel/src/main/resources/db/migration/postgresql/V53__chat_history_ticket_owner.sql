-- Nullable for legacy history: no ownership is inferred from a bare ticket_id.
ALTER TABLE chat_history ADD COLUMN ticket_owner_user_id BIGINT;

ALTER TABLE chat_history
    ADD CONSTRAINT fk_chat_history_ticket_owner
    FOREIGN KEY (ticket_owner_user_id, ticket_id)
    REFERENCES tickets(user_id, ticket_id);

CREATE INDEX idx_chat_history_canonical_ticket_recent
    ON chat_history(ticket_owner_user_id, ticket_id, id DESC)
    WHERE ticket_owner_user_id IS NOT NULL;

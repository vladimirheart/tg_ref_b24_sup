CREATE TABLE dialog_internal_read_markers (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    ticket_user_id BIGINT NOT NULL,
    ticket_id TEXT NOT NULL,
    ticket_public_id TEXT NOT NULL CHECK(ticket_public_id ~ '^[a-f0-9]{32}$'),
    last_read_id BIGINT NOT NULL CHECK(last_read_id>0),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY(user_id,ticket_public_id),
    FOREIGN KEY(ticket_user_id,ticket_id) REFERENCES tickets(user_id,ticket_id) ON DELETE CASCADE
);

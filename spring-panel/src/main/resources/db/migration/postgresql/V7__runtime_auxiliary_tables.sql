CREATE TABLE IF NOT EXISTS ticket_participants (
    ticket_id VARCHAR(255) NOT NULL,
    username VARCHAR(255) NOT NULL,
    added_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    added_by TEXT,
    PRIMARY KEY (ticket_id, username)
);

CREATE INDEX IF NOT EXISTS idx_ticket_participants_username
    ON ticket_participants(username);

CREATE TABLE IF NOT EXISTS ui_event_outbox (
    id BIGINT PRIMARY KEY,
    event_type TEXT NOT NULL,
    ticket_id VARCHAR(255) NOT NULL,
    channel_id BIGINT,
    message_text TEXT,
    message_type TEXT,
    attachment TEXT,
    rating INTEGER,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_ui_event_outbox_ticket
    ON ui_event_outbox(ticket_id, id);

CREATE TABLE IF NOT EXISTS chat_attachment_metadata (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    chat_history_id BIGINT NOT NULL UNIQUE REFERENCES chat_history(id) ON DELETE CASCADE,
    ticket_id VARCHAR(255),
    channel_id BIGINT,
    storage_key TEXT,
    storage_provider TEXT NOT NULL DEFAULT 'local_fs',
    storage_class TEXT NOT NULL DEFAULT 'dialog_attachment',
    original_name TEXT,
    mime_type TEXT,
    size BIGINT,
    content_hash TEXT,
    legacy_attachment_ref TEXT,
    normalization_status TEXT NOT NULL DEFAULT 'normalized',
    availability_status TEXT NOT NULL DEFAULT 'unknown',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE,
    archived_at TIMESTAMP WITH TIME ZONE,
    deleted_at TIMESTAMP WITH TIME ZONE,
    CHECK (normalization_status IN ('normalized', 'unresolved')),
    CHECK (availability_status IN ('available', 'missing', 'external', 'unresolved', 'unknown'))
);

CREATE INDEX IF NOT EXISTS idx_chat_attachment_metadata_ticket
    ON chat_attachment_metadata(ticket_id, chat_history_id);

CREATE INDEX IF NOT EXISTS idx_chat_attachment_metadata_storage_key
    ON chat_attachment_metadata(storage_key);

CREATE TABLE IF NOT EXISTS password_reset_requests (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    username_snapshot VARCHAR(255) NOT NULL,
    requested_by_username VARCHAR(255),
    requested_by_ip VARCHAR(255),
    requested_user_agent TEXT,
    requested_note TEXT,
    status TEXT NOT NULL DEFAULT 'PENDING',
    resolution_note TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    resolved_at TIMESTAMP WITH TIME ZONE,
    resolved_by_username VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_password_reset_requests_status_created_at
    ON password_reset_requests(status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_password_reset_requests_user_id
    ON password_reset_requests(user_id);

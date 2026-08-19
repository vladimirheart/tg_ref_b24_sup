ALTER TABLE bot_chat_history
    ADD COLUMN IF NOT EXISTS ticket_id TEXT;

ALTER TABLE bot_chat_history
    ADD COLUMN IF NOT EXISTS attachment_path TEXT;

CREATE TABLE IF NOT EXISTS legacy_bot_shard_imports (
    source_path TEXT PRIMARY KEY,
    source_size BIGINT NOT NULL,
    source_modified_at TIMESTAMP WITH TIME ZONE,
    imported_rows BIGINT NOT NULL DEFAULT 0,
    imported_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

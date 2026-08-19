ALTER TABLE bot_chat_history
    ADD COLUMN ticket_id TEXT;

ALTER TABLE bot_chat_history
    ADD COLUMN attachment_path TEXT;

CREATE TABLE IF NOT EXISTS legacy_bot_shard_imports (
    source_path TEXT PRIMARY KEY,
    source_size INTEGER NOT NULL,
    source_modified_at TEXT,
    imported_rows INTEGER NOT NULL DEFAULT 0,
    imported_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

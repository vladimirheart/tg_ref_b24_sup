ALTER TABLE bot_chat_history
    ADD COLUMN IF NOT EXISTS ticket_id TEXT NULL;

ALTER TABLE bot_chat_history
    ADD COLUMN IF NOT EXISTS attachment_path TEXT NULL;

CREATE TABLE IF NOT EXISTS legacy_bot_shard_imports (
    source_path VARCHAR(1024) NOT NULL,
    source_size BIGINT NOT NULL,
    source_modified_at DATETIME(6) NULL,
    imported_rows BIGINT NOT NULL DEFAULT 0,
    imported_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (source_path)
);

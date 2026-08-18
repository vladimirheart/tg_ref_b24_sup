-- Keep PostgreSQL schema aligned with runtime features that were added later
-- to the SQLite migration chain.

ALTER TABLE knowledge_articles
    ADD COLUMN IF NOT EXISTS external_source TEXT;

ALTER TABLE knowledge_articles
    ADD COLUMN IF NOT EXISTS external_id TEXT;

ALTER TABLE knowledge_articles
    ADD COLUMN IF NOT EXISTS external_url TEXT;

ALTER TABLE knowledge_articles
    ADD COLUMN IF NOT EXISTS external_updated_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX IF NOT EXISTS idx_knowledge_articles_external_source_id
    ON knowledge_articles(external_source, external_id);

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS last_portal_activity_at TIMESTAMP WITH TIME ZONE;

-- Records one-time imports from pre-PostgreSQL SQLite installations.
CREATE TABLE IF NOT EXISTS legacy_sqlite_imports (
    source_path TEXT PRIMARY KEY,
    source_size BIGINT NOT NULL DEFAULT 0,
    source_modified_at TIMESTAMP WITH TIME ZONE,
    imported_rows BIGINT NOT NULL DEFAULT 0,
    imported_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

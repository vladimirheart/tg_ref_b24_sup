CREATE TABLE IF NOT EXISTS knowledge_notes (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    title VARCHAR(500) NOT NULL,
    body TEXT,
    custom_fields JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_knowledge_notes_updated
    ON knowledge_notes(updated_at DESC, id DESC);

CREATE TABLE IF NOT EXISTS knowledge_note_links (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    note_id BIGINT NOT NULL REFERENCES knowledge_notes(id) ON DELETE CASCADE,
    target_type VARCHAR(40) NOT NULL,
    target_id BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_knowledge_note_link_target_type CHECK (target_type IN ('knowledge_article', 'object_passport')),
    CONSTRAINT uq_knowledge_note_link UNIQUE(note_id, target_type, target_id)
);

CREATE INDEX IF NOT EXISTS idx_knowledge_note_links_target
    ON knowledge_note_links(target_type, target_id, note_id);

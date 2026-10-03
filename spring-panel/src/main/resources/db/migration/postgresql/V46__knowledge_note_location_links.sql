CREATE TABLE IF NOT EXISTS knowledge_note_location_links (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    note_id BIGINT NOT NULL REFERENCES knowledge_notes(id) ON DELETE CASCADE,
    location_name TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_knowledge_note_location_link UNIQUE(note_id, location_name)
);

CREATE INDEX IF NOT EXISTS idx_knowledge_note_location_links_location
    ON knowledge_note_location_links(location_name, note_id);

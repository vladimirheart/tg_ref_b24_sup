CREATE TABLE object_passport_documents (
    id UUID PRIMARY KEY,
    passport_id BIGINT NOT NULL REFERENCES object_passports(id),
    current_revision INTEGER NOT NULL CHECK (current_revision > 0),
    archived BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_passport_documents_list ON object_passport_documents(passport_id, archived, created_at DESC, id);

-- Immutable snapshots: replacing a file or editing a note never overwrites an older revision.
CREATE TABLE object_passport_document_revisions (
    document_id UUID NOT NULL REFERENCES object_passport_documents(id),
    revision INTEGER NOT NULL CHECK (revision > 0),
    title VARCHAR(240) NOT NULL,
    category VARCHAR(120) NOT NULL DEFAULT '',
    event_date DATE,
    description TEXT NOT NULL DEFAULT '',
    fields JSONB NOT NULL DEFAULT '{}'::jsonb CHECK (jsonb_typeof(fields) = 'object'),
    archived BOOLEAN NOT NULL DEFAULT FALSE,
    storage_key TEXT,
    original_name TEXT,
    media_type TEXT,
    file_size BIGINT CHECK (file_size > 0),
    preview_kind VARCHAR(10) NOT NULL DEFAULT 'none' CHECK (preview_kind IN ('none', 'image', 'pdf', 'text')),
    preview_text TEXT,
    action VARCHAR(16) NOT NULL CHECK (action IN ('create', 'edit', 'archive', 'unarchive')),
    actor TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (document_id, revision),
    CHECK ((storage_key IS NULL AND original_name IS NULL AND file_size IS NULL)
        OR (storage_key IS NOT NULL AND original_name IS NOT NULL AND media_type IS NOT NULL AND file_size IS NOT NULL))
);

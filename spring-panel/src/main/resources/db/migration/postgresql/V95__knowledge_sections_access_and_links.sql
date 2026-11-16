CREATE TABLE IF NOT EXISTS knowledge_sections (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    parent_id BIGINT REFERENCES knowledge_sections(id) ON DELETE RESTRICT,
    code VARCHAR(80) UNIQUE,
    title VARCHAR(500) NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO knowledge_sections(code, title, sort_order)
VALUES ('general', 'Общее', 0)
ON CONFLICT (code) DO NOTHING;

ALTER TABLE knowledge_articles ADD COLUMN IF NOT EXISTS section_id BIGINT;

UPDATE knowledge_articles
SET section_id = (SELECT id FROM knowledge_sections WHERE code = 'general')
WHERE section_id IS NULL;

ALTER TABLE knowledge_articles
    ADD CONSTRAINT fk_knowledge_articles_section
    FOREIGN KEY (section_id) REFERENCES knowledge_sections(id) ON DELETE RESTRICT;

ALTER TABLE knowledge_articles
    ALTER COLUMN section_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_knowledge_sections_parent_sort
    ON knowledge_sections(parent_id, sort_order, id);
CREATE INDEX IF NOT EXISTS idx_knowledge_articles_section_updated
    ON knowledge_articles(section_id, updated_at DESC, id DESC);

CREATE TABLE IF NOT EXISTS knowledge_resource_acl (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    resource_type VARCHAR(16) NOT NULL CHECK (resource_type IN ('SECTION', 'ARTICLE')),
    resource_id BIGINT NOT NULL,
    subject_type VARCHAR(16) NOT NULL CHECK (subject_type IN ('ROLE', 'USER')),
    subject_id BIGINT NOT NULL,
    access_level VARCHAR(16) NOT NULL CHECK (access_level IN ('VIEW', 'EDIT', 'MANAGE')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_knowledge_resource_acl UNIQUE(resource_type, resource_id, subject_type, subject_id)
);

CREATE INDEX IF NOT EXISTS idx_knowledge_resource_acl_resource
    ON knowledge_resource_acl(resource_type, resource_id);
CREATE INDEX IF NOT EXISTS idx_knowledge_resource_acl_subject
    ON knowledge_resource_acl(subject_type, subject_id, access_level);

CREATE TABLE IF NOT EXISTS knowledge_article_links (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source_article_id BIGINT NOT NULL REFERENCES knowledge_articles(id) ON DELETE CASCADE,
    target_article_id BIGINT NOT NULL REFERENCES knowledge_articles(id) ON DELETE CASCADE,
    source_reference VARCHAR(500) NOT NULL,
    link_label VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_knowledge_article_link UNIQUE(source_article_id, source_reference)
);

CREATE INDEX IF NOT EXISTS idx_knowledge_article_links_target
    ON knowledge_article_links(target_article_id, source_article_id);

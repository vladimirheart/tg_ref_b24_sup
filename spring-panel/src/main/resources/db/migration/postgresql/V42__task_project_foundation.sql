CREATE TABLE IF NOT EXISTS projects (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    project_key VARCHAR(80) UNIQUE,
    name VARCHAR(240) NOT NULL,
    description TEXT,
    status VARCHAR(40) NOT NULL DEFAULT 'active',
    lead_identity VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    archived_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_projects_status_name
    ON projects(status, name);

CREATE TABLE IF NOT EXISTS task_project_memberships (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    task_id BIGINT NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    project_id BIGINT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    added_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    added_by VARCHAR(255),
    CONSTRAINT uq_task_project_membership UNIQUE(task_id, project_id)
);

CREATE INDEX IF NOT EXISTS idx_task_project_memberships_project_task
    ON task_project_memberships(project_id, task_id);

CREATE TABLE IF NOT EXISTS tags (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    normalized_name VARCHAR(120) NOT NULL UNIQUE,
    color VARCHAR(32),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS task_tags (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    task_id BIGINT NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    tag_id BIGINT NOT NULL REFERENCES tags(id) ON DELETE CASCADE,
    added_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    added_by VARCHAR(255),
    CONSTRAINT uq_task_tag UNIQUE(task_id, tag_id)
);

CREATE INDEX IF NOT EXISTS idx_task_tags_tag_task
    ON task_tags(tag_id, task_id);

CREATE TABLE IF NOT EXISTS task_events (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    task_id BIGINT NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    project_id BIGINT REFERENCES projects(id) ON DELETE SET NULL,
    event_type VARCHAR(80) NOT NULL,
    actor VARCHAR(255),
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    field_name VARCHAR(120),
    old_value TEXT,
    new_value TEXT,
    metadata_json TEXT
);

CREATE INDEX IF NOT EXISTS idx_task_events_task_time
    ON task_events(task_id, occurred_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_task_events_type_time
    ON task_events(event_type, occurred_at DESC);

WITH legacy_tags AS (
    SELECT t.id AS task_id,
           t.created_at,
           BTRIM(BTRIM(piece), '# ') AS name
      FROM tasks t
      CROSS JOIN LATERAL regexp_split_to_table(COALESCE(t.tag, ''), E'[,;\\r\\n]+') AS piece
), cleaned_tags AS (
    SELECT task_id,
           created_at,
           name,
           LOWER(name) AS normalized_name
      FROM legacy_tags
     WHERE name <> ''
)
INSERT INTO tags(name, normalized_name, created_at)
SELECT MIN(name), normalized_name, CURRENT_TIMESTAMP
  FROM cleaned_tags
 GROUP BY normalized_name
ON CONFLICT (normalized_name) DO NOTHING;

WITH legacy_tags AS (
    SELECT t.id AS task_id,
           t.created_at,
           BTRIM(BTRIM(piece), '# ') AS name
      FROM tasks t
      CROSS JOIN LATERAL regexp_split_to_table(COALESCE(t.tag, ''), E'[,;\\r\\n]+') AS piece
), cleaned_tags AS (
    SELECT task_id,
           created_at,
           LOWER(name) AS normalized_name
      FROM legacy_tags
     WHERE name <> ''
)
INSERT INTO task_tags(task_id, tag_id, added_at)
SELECT c.task_id,
       g.id,
       COALESCE(c.created_at, CURRENT_TIMESTAMP)
  FROM cleaned_tags c
  JOIN tags g ON g.normalized_name = c.normalized_name
ON CONFLICT (task_id, tag_id) DO NOTHING;

INSERT INTO task_events(task_id, event_type, actor, occurred_at)
SELECT t.id,
       'TASK_CREATED',
       t.creator,
       COALESCE(t.created_at, CURRENT_TIMESTAMP)
  FROM tasks t
 WHERE NOT EXISTS (
       SELECT 1
         FROM task_events e
        WHERE e.task_id = t.id
          AND e.event_type = 'TASK_CREATED'
 );

CREATE TABLE IF NOT EXISTS projects (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    project_key TEXT UNIQUE,
    name TEXT NOT NULL,
    description TEXT,
    status TEXT NOT NULL DEFAULT 'active',
    lead_identity TEXT,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    archived_at TEXT
);

CREATE INDEX IF NOT EXISTS idx_projects_status_name
    ON projects(status, name);

CREATE TABLE IF NOT EXISTS task_project_memberships (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    task_id INTEGER NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    project_id INTEGER NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    added_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    added_by TEXT,
    UNIQUE(task_id, project_id)
);

CREATE INDEX IF NOT EXISTS idx_task_project_memberships_project_task
    ON task_project_memberships(project_id, task_id);

CREATE TABLE IF NOT EXISTS tags (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    normalized_name TEXT NOT NULL UNIQUE,
    color TEXT,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS task_tags (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    task_id INTEGER NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    tag_id INTEGER NOT NULL REFERENCES tags(id) ON DELETE CASCADE,
    added_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    added_by TEXT,
    UNIQUE(task_id, tag_id)
);

CREATE INDEX IF NOT EXISTS idx_task_tags_tag_task
    ON task_tags(tag_id, task_id);

CREATE TABLE IF NOT EXISTS task_events (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    task_id INTEGER NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    project_id INTEGER REFERENCES projects(id) ON DELETE SET NULL,
    event_type TEXT NOT NULL,
    actor TEXT,
    occurred_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    field_name TEXT,
    old_value TEXT,
    new_value TEXT,
    metadata_json TEXT
);

CREATE INDEX IF NOT EXISTS idx_task_events_task_time
    ON task_events(task_id, occurred_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_task_events_type_time
    ON task_events(event_type, occurred_at DESC);

WITH RECURSIVE source_tags(task_id, created_at, rest) AS (
    SELECT id,
           created_at,
           replace(replace(replace(COALESCE(tag, ''), ';', ','), char(10), ','), char(13), ',') || ','
      FROM tasks
), split_tags(task_id, created_at, rest, part) AS (
    SELECT task_id, created_at, rest, ''
      FROM source_tags
    UNION ALL
    SELECT task_id,
           created_at,
           substr(rest, instr(rest, ',') + 1),
           substr(rest, 1, instr(rest, ',') - 1)
      FROM split_tags
     WHERE rest <> ''
), cleaned_tags(task_id, created_at, name, normalized_name) AS (
    SELECT task_id,
           created_at,
           trim(part, '# '),
           lower(trim(part, '# '))
      FROM split_tags
     WHERE trim(part, '# ') <> ''
)
INSERT OR IGNORE INTO tags(name, normalized_name, created_at)
SELECT min(name), normalized_name, CURRENT_TIMESTAMP
  FROM cleaned_tags
 GROUP BY normalized_name;

WITH RECURSIVE source_tags(task_id, created_at, rest) AS (
    SELECT id,
           created_at,
           replace(replace(replace(COALESCE(tag, ''), ';', ','), char(10), ','), char(13), ',') || ','
      FROM tasks
), split_tags(task_id, created_at, rest, part) AS (
    SELECT task_id, created_at, rest, ''
      FROM source_tags
    UNION ALL
    SELECT task_id,
           created_at,
           substr(rest, instr(rest, ',') + 1),
           substr(rest, 1, instr(rest, ',') - 1)
      FROM split_tags
     WHERE rest <> ''
), cleaned_tags(task_id, created_at, normalized_name) AS (
    SELECT task_id,
           created_at,
           lower(trim(part, '# '))
      FROM split_tags
     WHERE trim(part, '# ') <> ''
)
INSERT OR IGNORE INTO task_tags(task_id, tag_id, added_at)
SELECT c.task_id,
       g.id,
       COALESCE(c.created_at, CURRENT_TIMESTAMP)
  FROM cleaned_tags c
  JOIN tags g ON g.normalized_name = c.normalized_name;

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

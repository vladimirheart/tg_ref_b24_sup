CREATE TABLE IF NOT EXISTS projects (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_key VARCHAR(80) UNIQUE,
    name VARCHAR(240) NOT NULL,
    description TEXT,
    status VARCHAR(40) NOT NULL DEFAULT 'active',
    lead_identity VARCHAR(255),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    archived_at DATETIME(6)
);

CREATE INDEX idx_projects_status_name
    ON projects(status, name);

CREATE TABLE IF NOT EXISTS task_project_memberships (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    added_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    added_by VARCHAR(255),
    CONSTRAINT uq_task_project_membership UNIQUE(task_id, project_id),
    CONSTRAINT fk_task_project_membership_task FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE,
    CONSTRAINT fk_task_project_membership_project FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE
);

CREATE INDEX idx_task_project_memberships_project_task
    ON task_project_memberships(project_id, task_id);

CREATE TABLE IF NOT EXISTS tags (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    normalized_name VARCHAR(120) NOT NULL UNIQUE,
    color VARCHAR(32),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);

CREATE TABLE IF NOT EXISTS task_tags (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    added_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    added_by VARCHAR(255),
    CONSTRAINT uq_task_tag UNIQUE(task_id, tag_id),
    CONSTRAINT fk_task_tag_task FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE,
    CONSTRAINT fk_task_tag_tag FOREIGN KEY (tag_id) REFERENCES tags(id) ON DELETE CASCADE
);

CREATE INDEX idx_task_tags_tag_task
    ON task_tags(tag_id, task_id);

CREATE TABLE IF NOT EXISTS task_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    project_id BIGINT,
    event_type VARCHAR(80) NOT NULL,
    actor VARCHAR(255),
    occurred_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    field_name VARCHAR(120),
    old_value TEXT,
    new_value TEXT,
    metadata_json LONGTEXT,
    CONSTRAINT fk_task_event_task FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE,
    CONSTRAINT fk_task_event_project FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE SET NULL
);

CREATE INDEX idx_task_events_task_time
    ON task_events(task_id, occurred_at DESC, id DESC);

CREATE INDEX idx_task_events_type_time
    ON task_events(event_type, occurred_at DESC);

INSERT IGNORE INTO tags(name, normalized_name, created_at)
SELECT DISTINCT
       TRIM(TRIM(BOTH '#' FROM TRIM(t.tag))),
       LOWER(TRIM(TRIM(BOTH '#' FROM TRIM(t.tag)))),
       CURRENT_TIMESTAMP(6)
  FROM tasks t
 WHERE t.tag IS NOT NULL
   AND TRIM(TRIM(BOTH '#' FROM TRIM(t.tag))) <> '';

INSERT IGNORE INTO task_tags(task_id, tag_id, added_at)
SELECT t.id,
       g.id,
       COALESCE(t.created_at, CURRENT_TIMESTAMP(6))
  FROM tasks t
  JOIN tags g
    ON g.normalized_name = LOWER(TRIM(TRIM(BOTH '#' FROM TRIM(t.tag))))
 WHERE t.tag IS NOT NULL
   AND TRIM(TRIM(BOTH '#' FROM TRIM(t.tag))) <> '';

INSERT INTO task_events(task_id, event_type, actor, occurred_at)
SELECT t.id,
       'TASK_CREATED',
       t.creator,
       COALESCE(t.created_at, CURRENT_TIMESTAMP(6))
  FROM tasks t
 WHERE NOT EXISTS (
       SELECT 1
         FROM task_events e
        WHERE e.task_id = t.id
          AND e.event_type = 'TASK_CREATED'
 );

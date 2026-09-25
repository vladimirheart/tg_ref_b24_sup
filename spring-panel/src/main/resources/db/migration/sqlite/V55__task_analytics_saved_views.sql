CREATE TABLE IF NOT EXISTS task_analytics_views (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    owner_identity TEXT NOT NULL,
    name TEXT NOT NULL,
    filters_json TEXT NOT NULL,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(owner_identity, name)
);

CREATE INDEX IF NOT EXISTS idx_task_analytics_views_owner_updated
    ON task_analytics_views(owner_identity, updated_at, id);

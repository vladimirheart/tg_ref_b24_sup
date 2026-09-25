CREATE TABLE IF NOT EXISTS task_analytics_views (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    owner_identity VARCHAR(255) NOT NULL,
    name VARCHAR(120) NOT NULL,
    filters_json LONGTEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_task_analytics_view_owner_name UNIQUE(owner_identity, name)
);

CREATE INDEX idx_task_analytics_views_owner_updated
    ON task_analytics_views(owner_identity, updated_at, id);

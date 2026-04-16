CREATE TABLE IF NOT EXISTS automation_runs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    automation_key VARCHAR(128) NOT NULL,
    mode VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    actor VARCHAR(255),
    summary TEXT,
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at TIMESTAMP NULL DEFAULT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_automation_runs_key_started
    ON automation_runs(automation_key, started_at DESC);

CREATE TABLE IF NOT EXISTS automation_run_items (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    run_id BIGINT NOT NULL,
    external_task_id VARCHAR(255),
    task_title TEXT,
    phone VARCHAR(64),
    status VARCHAR(32) NOT NULL,
    message TEXT,
    checklist_item_id VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_automation_run_items_run
        FOREIGN KEY (run_id) REFERENCES automation_runs(id) ON DELETE CASCADE
);

CREATE INDEX idx_automation_run_items_run
    ON automation_run_items(run_id, created_at ASC);

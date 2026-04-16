CREATE TABLE IF NOT EXISTS automation_runs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    automation_key TEXT NOT NULL,
    mode TEXT NOT NULL,
    status TEXT NOT NULL,
    actor TEXT,
    summary TEXT,
    started_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at TEXT,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_automation_runs_key_started
    ON automation_runs(automation_key, started_at DESC);

CREATE TABLE IF NOT EXISTS automation_run_items (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    run_id INTEGER NOT NULL REFERENCES automation_runs(id) ON DELETE CASCADE,
    external_task_id TEXT,
    task_title TEXT,
    phone TEXT,
    status TEXT NOT NULL,
    message TEXT,
    checklist_item_id TEXT,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_automation_run_items_run
    ON automation_run_items(run_id, created_at ASC);

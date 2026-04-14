CREATE TABLE IF NOT EXISTS ai_agent_solution_memory_history (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    query_key TEXT NOT NULL,
    changed_by TEXT,
    change_source TEXT NOT NULL DEFAULT 'manual',
    change_action TEXT NOT NULL DEFAULT 'update',
    old_query_text TEXT,
    old_solution_text TEXT,
    old_review_required INTEGER NOT NULL DEFAULT 0,
    new_query_text TEXT,
    new_solution_text TEXT,
    new_review_required INTEGER NOT NULL DEFAULT 0,
    note TEXT,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ai_agent_solution_memory_history_key_created
    ON ai_agent_solution_memory_history(query_key, created_at DESC);

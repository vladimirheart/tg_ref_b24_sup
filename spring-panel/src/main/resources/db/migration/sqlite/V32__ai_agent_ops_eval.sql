CREATE TABLE IF NOT EXISTS ai_agent_offline_eval_run (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    dataset_version TEXT NOT NULL,
    actor TEXT,
    cases_total INTEGER NOT NULL DEFAULT 0,
    cases_passed INTEGER NOT NULL DEFAULT 0,
    intent_accuracy REAL NOT NULL DEFAULT 0,
    policy_accuracy REAL NOT NULL DEFAULT 0,
    retrieval_hit_rate REAL NOT NULL DEFAULT 0,
    confirmed_reply_rate REAL NOT NULL DEFAULT 0,
    details_json TEXT,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ai_agent_offline_eval_run_created
    ON ai_agent_offline_eval_run(created_at DESC);

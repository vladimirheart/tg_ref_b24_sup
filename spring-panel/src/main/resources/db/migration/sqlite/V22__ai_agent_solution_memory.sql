CREATE TABLE IF NOT EXISTS ai_agent_solution_memory (
    query_key             TEXT PRIMARY KEY,
    query_text            TEXT NOT NULL,
    solution_text         TEXT NOT NULL,
    source                TEXT NOT NULL DEFAULT 'operator',
    times_used            INTEGER NOT NULL DEFAULT 0,
    times_confirmed       INTEGER NOT NULL DEFAULT 0,
    times_corrected       INTEGER NOT NULL DEFAULT 0,
    review_required       BOOLEAN NOT NULL DEFAULT FALSE,
    pending_solution_text TEXT,
    last_operator         TEXT,
    created_at            TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ai_agent_solution_memory_review
    ON ai_agent_solution_memory(review_required, updated_at);


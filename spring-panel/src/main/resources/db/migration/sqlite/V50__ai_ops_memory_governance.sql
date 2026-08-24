ALTER TABLE ai_agent_solution_memory
    ADD COLUMN auto_reply_allowed INTEGER NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_ai_agent_solution_memory_runtime_policy
    ON ai_agent_solution_memory(status, review_required, auto_reply_allowed, updated_at DESC);
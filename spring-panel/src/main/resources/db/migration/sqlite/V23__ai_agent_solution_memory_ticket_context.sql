ALTER TABLE ai_agent_solution_memory
    ADD COLUMN last_ticket_id TEXT;

ALTER TABLE ai_agent_solution_memory
    ADD COLUMN last_client_message TEXT;

CREATE INDEX IF NOT EXISTS idx_ai_agent_solution_memory_ticket
    ON ai_agent_solution_memory(last_ticket_id, updated_at);


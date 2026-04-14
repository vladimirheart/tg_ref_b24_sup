CREATE TABLE IF NOT EXISTS ticket_ai_agent_state (
    ticket_id            VARCHAR(255) PRIMARY KEY,
    is_processing        BOOLEAN NOT NULL DEFAULT FALSE,
    mode                 TEXT,
    last_action          TEXT,
    last_error           TEXT,
    last_source          TEXT,
    last_score           REAL,
    last_suggested_reply TEXT,
    updated_at           TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ticket_ai_agent_state_processing
    ON ticket_ai_agent_state(is_processing);

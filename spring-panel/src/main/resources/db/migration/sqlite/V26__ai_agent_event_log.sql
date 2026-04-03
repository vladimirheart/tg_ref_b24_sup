CREATE TABLE IF NOT EXISTS ai_agent_event_log (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    ticket_id TEXT,
    event_type TEXT NOT NULL,
    actor TEXT,
    decision_type TEXT,
    decision_reason TEXT,
    source TEXT,
    score REAL,
    detail TEXT,
    payload_json TEXT,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ai_agent_event_log_created
    ON ai_agent_event_log(created_at DESC);

CREATE INDEX IF NOT EXISTS idx_ai_agent_event_log_ticket_created
    ON ai_agent_event_log(ticket_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_ai_agent_event_log_type_created
    ON ai_agent_event_log(event_type, created_at DESC);

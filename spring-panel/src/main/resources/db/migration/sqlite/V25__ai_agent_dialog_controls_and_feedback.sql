CREATE TABLE IF NOT EXISTS ticket_ai_agent_dialog_control (
    ticket_id TEXT PRIMARY KEY,
    ai_disabled INTEGER NOT NULL DEFAULT 0,
    auto_reply_blocked INTEGER NOT NULL DEFAULT 0,
    reason TEXT,
    updated_by TEXT,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ai_agent_suggestion_feedback (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    ticket_id TEXT NOT NULL,
    decision TEXT NOT NULL,
    source TEXT,
    title TEXT,
    snippet TEXT,
    suggested_reply TEXT,
    actor TEXT,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ai_agent_suggestion_feedback_ticket_created
    ON ai_agent_suggestion_feedback(ticket_id, created_at DESC);

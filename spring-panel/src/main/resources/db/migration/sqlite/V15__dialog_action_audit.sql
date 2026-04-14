CREATE TABLE IF NOT EXISTS dialog_action_audit (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    ticket_id  TEXT NOT NULL,
    actor      TEXT,
    action     TEXT NOT NULL,
    result     TEXT,
    detail     TEXT,
    created_at TEXT DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_dialog_action_audit_ticket_created
    ON dialog_action_audit(ticket_id, created_at DESC);

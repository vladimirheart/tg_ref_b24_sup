CREATE TABLE IF NOT EXISTS dialog_action_audit (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ticket_id TEXT NOT NULL,
    actor TEXT NOT NULL DEFAULT 'anonymous',
    action TEXT NOT NULL,
    result TEXT NOT NULL DEFAULT 'unknown',
    detail TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_dialog_action_audit_ticket_action_result
    ON dialog_action_audit(ticket_id, action, result);

CREATE INDEX IF NOT EXISTS idx_dialog_action_audit_created_at
    ON dialog_action_audit(created_at DESC);

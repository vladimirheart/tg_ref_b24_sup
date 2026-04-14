CREATE TABLE IF NOT EXISTS dialog_action_audit (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticket_id TEXT NOT NULL,
    actor TEXT,
    action TEXT NOT NULL,
    result TEXT,
    detail TEXT,
    created_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6)
);

CREATE INDEX idx_dialog_action_audit_ticket_created
    ON dialog_action_audit(ticket_id(191), created_at);

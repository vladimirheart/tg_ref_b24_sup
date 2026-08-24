-- Retention delete scans monitoring history by created_at across the canonical PostgreSQL runtime.
CREATE INDEX IF NOT EXISTS idx_monitoring_check_history_created_at
    ON monitoring_check_history(created_at);
CREATE TABLE IF NOT EXISTS workspace_telemetry_audit (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    actor TEXT,
    event_type TEXT NOT NULL,
    event_group TEXT,
    ticket_id TEXT,
    reason TEXT,
    error_code TEXT,
    contract_version TEXT,
    duration_ms INTEGER,
    experiment_name TEXT,
    experiment_cohort TEXT,
    operator_segment TEXT,
    primary_kpis TEXT,
    secondary_kpis TEXT,
    template_id TEXT,
    template_name TEXT,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_workspace_telemetry_created
    ON workspace_telemetry_audit(created_at DESC);

CREATE INDEX IF NOT EXISTS idx_workspace_telemetry_experiment
    ON workspace_telemetry_audit(experiment_name, experiment_cohort, operator_segment);

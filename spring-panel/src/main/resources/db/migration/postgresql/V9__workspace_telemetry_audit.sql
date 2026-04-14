CREATE TABLE IF NOT EXISTS workspace_telemetry_audit (
    id BIGSERIAL PRIMARY KEY,
    actor VARCHAR(191),
    event_type VARCHAR(191) NOT NULL,
    event_group VARCHAR(191),
    ticket_id VARCHAR(191),
    reason VARCHAR(255),
    error_code VARCHAR(191),
    contract_version VARCHAR(64),
    duration_ms BIGINT,
    experiment_name VARCHAR(191),
    experiment_cohort VARCHAR(191),
    operator_segment VARCHAR(191),
    primary_kpis TEXT,
    secondary_kpis TEXT,
    template_id VARCHAR(191),
    template_name VARCHAR(191),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_workspace_telemetry_created
    ON workspace_telemetry_audit(created_at DESC);

CREATE INDEX IF NOT EXISTS idx_workspace_telemetry_experiment
    ON workspace_telemetry_audit(experiment_name, experiment_cohort, operator_segment);

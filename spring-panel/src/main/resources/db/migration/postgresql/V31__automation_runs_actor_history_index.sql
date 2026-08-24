CREATE INDEX IF NOT EXISTS idx_automation_runs_key_actor_started
    ON automation_runs(automation_key, lower(actor), started_at DESC);

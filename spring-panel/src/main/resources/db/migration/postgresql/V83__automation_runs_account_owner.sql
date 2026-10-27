-- actor is an immutable display snapshot, never an ownership key.
-- Existing test rows remain unbound; do not infer an account from a name.
ALTER TABLE automation_runs ADD COLUMN actor_user_id BIGINT;

ALTER TABLE automation_runs
    ADD CONSTRAINT ck_automation_runs_actor_user_id_positive
    CHECK (actor_user_id IS NULL OR actor_user_id > 0);

CREATE INDEX idx_automation_runs_key_actor_user_started_id
    ON automation_runs(automation_key, actor_user_id, started_at DESC, id DESC);

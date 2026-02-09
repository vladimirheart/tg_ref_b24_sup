CREATE TABLE IF NOT EXISTS client_blacklist_history (
    id BIGSERIAL PRIMARY KEY,
    user_id TEXT NOT NULL,
    action TEXT NOT NULL,
    reason TEXT,
    actor TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_client_blacklist_history_user ON client_blacklist_history(user_id);

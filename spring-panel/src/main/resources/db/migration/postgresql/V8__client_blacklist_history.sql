CREATE TABLE IF NOT EXISTS client_blacklist_history (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id TEXT NOT NULL,
    action TEXT NOT NULL,
    reason TEXT,
    actor TEXT,
    created_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_client_blacklist_history_user ON client_blacklist_history(user_id);

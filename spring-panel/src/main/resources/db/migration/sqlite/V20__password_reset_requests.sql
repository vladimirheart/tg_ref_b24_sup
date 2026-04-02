CREATE TABLE IF NOT EXISTS password_reset_requests (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER,
    username_snapshot TEXT NOT NULL,
    requested_by_username TEXT,
    requested_by_ip TEXT,
    requested_user_agent TEXT,
    requested_note TEXT,
    status TEXT NOT NULL DEFAULT 'PENDING',
    resolution_note TEXT,
    created_at TEXT NOT NULL,
    resolved_at TEXT,
    resolved_by_username TEXT,
    FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_password_reset_requests_status_created_at
    ON password_reset_requests(status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_password_reset_requests_user_id
    ON password_reset_requests(user_id);

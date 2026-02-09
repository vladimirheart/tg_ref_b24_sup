CREATE TABLE IF NOT EXISTS client_blacklist_history (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id VARCHAR(255) NOT NULL,
    action VARCHAR(32) NOT NULL,
    reason TEXT,
    actor VARCHAR(255),
    created_at DATETIME(6) NOT NULL
);

CREATE INDEX idx_client_blacklist_history_user ON client_blacklist_history(user_id);

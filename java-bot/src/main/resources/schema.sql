-- Initialize the SQLite database with the tables required by the legacy bot.
CREATE TABLE IF NOT EXISTS channels (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    token TEXT NOT NULL UNIQUE,
    bot_name TEXT,
    channel_name TEXT NOT NULL,
    questions_cfg TEXT,
    max_questions INTEGER DEFAULT 0,
    is_active INTEGER DEFAULT 1,
    created_at TEXT DEFAULT (datetime('now')),
    bot_username TEXT,
    question_template_id TEXT,
    rating_template_id TEXT,
    public_id TEXT
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_channels_public_id ON channels(public_id);

CREATE TABLE IF NOT EXISTS client_blacklist (
    user_id TEXT PRIMARY KEY,
    is_blacklisted INTEGER NOT NULL DEFAULT 0,
    reason TEXT,
    added_at TEXT,
    added_by TEXT,
    unblock_requested INTEGER NOT NULL DEFAULT 0,
    unblock_requested_at TEXT
);

CREATE TABLE IF NOT EXISTS client_unblock_requests (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id TEXT NOT NULL,
    channel_id INTEGER,
    reason TEXT,
    created_at TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'pending',
    decided_at TEXT,
    decided_by TEXT,
    decision_comment TEXT
);

CREATE INDEX IF NOT EXISTS idx_client_unblock_requests_user
    ON client_unblock_requests(user_id);

CREATE TABLE IF NOT EXISTS users (
    user_id INTEGER PRIMARY KEY,
    username TEXT,
    first_name TEXT,
    last_name TEXT,
    registered_at TEXT DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS chat_history (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER,
    message TEXT,
    timestamp TEXT,
    message_id INTEGER,
    message_type TEXT DEFAULT 'text'
);

CREATE TABLE IF NOT EXISTS applications (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER,
    problem_description TEXT,
    photo_path TEXT,
    status TEXT DEFAULT 'new',
    created_at TEXT DEFAULT (datetime('now')),
    b24_contact_id INTEGER,
    b24_deal_id INTEGER
);
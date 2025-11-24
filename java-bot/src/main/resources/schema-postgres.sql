CREATE TABLE IF NOT EXISTS channels (
    id BIGSERIAL PRIMARY KEY,
    token VARCHAR(128) NOT NULL UNIQUE,
    bot_name VARCHAR(255),
    channel_name VARCHAR(255) NOT NULL,
    questions_cfg TEXT,
    max_questions INTEGER DEFAULT 0,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    bot_username VARCHAR(255),
    question_template_id VARCHAR(255),
    rating_template_id VARCHAR(255),
    public_id VARCHAR(255)
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_channels_public_id ON channels(public_id);

CREATE TABLE IF NOT EXISTS client_blacklist (
    user_id VARCHAR(255) PRIMARY KEY,
    is_blacklisted BOOLEAN NOT NULL DEFAULT FALSE,
    reason TEXT,
    added_at TIMESTAMP,
    added_by VARCHAR(255),
    unblock_requested BOOLEAN NOT NULL DEFAULT FALSE,
    unblock_requested_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS client_unblock_requests (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    channel_id BIGINT,
    reason TEXT,
    created_at TIMESTAMP NOT NULL,
    status VARCHAR(64) NOT NULL DEFAULT 'pending',
    decided_at TIMESTAMP,
    decided_by VARCHAR(255),
    decision_comment TEXT
);

CREATE INDEX IF NOT EXISTS idx_client_unblock_requests_user
    ON client_unblock_requests(user_id);

CREATE TABLE IF NOT EXISTS users (
    user_id BIGINT PRIMARY KEY,
    username VARCHAR(255),
    first_name VARCHAR(255),
    last_name VARCHAR(255),
    registered_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS chat_history (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT,
    message TEXT,
    timestamp TIMESTAMP,
    message_id BIGINT,
    message_type VARCHAR(32) DEFAULT 'text'
);

CREATE TABLE IF NOT EXISTS applications (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT,
    problem_description TEXT,
    photo_path TEXT,
    status VARCHAR(32) DEFAULT 'new',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    b24_contact_id BIGINT,
    b24_deal_id BIGINT
);

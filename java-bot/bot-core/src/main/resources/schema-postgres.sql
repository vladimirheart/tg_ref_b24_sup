-- Initialize PostgreSQL schema aligned with the Java bot and panel.

CREATE TABLE IF NOT EXISTS bot_credentials (
    id SERIAL PRIMARY KEY,
    name TEXT NOT NULL,
    platform TEXT NOT NULL DEFAULT 'telegram',
    encrypted_token TEXT NOT NULL,
    metadata JSONB DEFAULT '{}'::jsonb,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS channels (
    id SERIAL PRIMARY KEY,
    token TEXT NOT NULL UNIQUE,
    bot_name TEXT,
    bot_username TEXT,
    channel_name TEXT NOT NULL DEFAULT 'Telegram',
    questions_cfg JSONB DEFAULT '{}'::jsonb,
    max_questions INTEGER DEFAULT 0,
    is_active BOOLEAN DEFAULT TRUE,
    question_template_id TEXT,
    rating_template_id TEXT,
    auto_action_template_id TEXT,
    public_id TEXT,
    description TEXT,
    filters JSONB DEFAULT '{}'::jsonb,
    delivery_settings JSONB DEFAULT '{}'::jsonb,
    platform TEXT NOT NULL DEFAULT 'telegram',
    platform_config JSONB DEFAULT '{}'::jsonb,
    credential_id INTEGER REFERENCES bot_credentials(id),
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    support_chat_id TEXT
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_channels_public_id ON channels(public_id);

CREATE TABLE IF NOT EXISTS channel_notifications (
    id SERIAL PRIMARY KEY,
    channel_id INTEGER NOT NULL REFERENCES channels(id) ON DELETE CASCADE,
    recipient TEXT,
    payload JSONB NOT NULL,
    status TEXT NOT NULL DEFAULT 'pending',
    error TEXT,
    attempts INTEGER NOT NULL DEFAULT 0,
    scheduled_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS chat_history (
    id SERIAL PRIMARY KEY,
    user_id BIGINT,
    sender TEXT,
    message TEXT,
    timestamp TIMESTAMPTZ,
    ticket_id TEXT,
    message_type TEXT DEFAULT 'text',
    attachment TEXT,
    channel_id INTEGER REFERENCES channels(id),
    tg_message_id BIGINT,
    reply_to_tg_id BIGINT,
    original_message TEXT,
    forwarded_from TEXT,
    edited_at TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_history_channel_time ON chat_history(channel_id, timestamp);
CREATE INDEX IF NOT EXISTS idx_history_ticket_channel ON chat_history(ticket_id, channel_id);

CREATE TABLE IF NOT EXISTS tickets (
    user_id BIGINT,
    group_msg_id BIGINT,
    status TEXT DEFAULT 'pending',
    resolved_at TIMESTAMPTZ,
    resolved_by TEXT,
    ticket_id TEXT UNIQUE,
    channel_id INTEGER REFERENCES channels(id),
    reopen_count INTEGER DEFAULT 0,
    closed_count INTEGER DEFAULT 0,
    work_time_total_sec BIGINT DEFAULT 0,
    last_reopen_at TIMESTAMPTZ,
    PRIMARY KEY (user_id, ticket_id)
);

CREATE INDEX IF NOT EXISTS idx_tickets_channel ON tickets(channel_id);
CREATE UNIQUE INDEX IF NOT EXISTS ux_tickets_ticketid_channel ON tickets(ticket_id, channel_id);

CREATE TABLE IF NOT EXISTS messages (
    group_msg_id BIGINT PRIMARY KEY,
    user_id BIGINT,
    business TEXT,
    location_type TEXT,
    city TEXT,
    location_name TEXT,
    problem TEXT,
    created_at TIMESTAMPTZ,
    username TEXT DEFAULT '',
    category TEXT,
    ticket_id TEXT UNIQUE,
    created_date DATE,
    created_time TEXT,
    client_name TEXT,
    client_status TEXT,
    channel_id INTEGER REFERENCES channels(id),
    updated_at TIMESTAMPTZ,
    updated_by TEXT
);

CREATE INDEX IF NOT EXISTS idx_messages_channel ON messages(channel_id);

CREATE TABLE IF NOT EXISTS client_statuses (
    user_id BIGINT PRIMARY KEY,
    status TEXT DEFAULT 'Не указан',
    updated_at TIMESTAMPTZ,
    updated_by TEXT
);

CREATE TABLE IF NOT EXISTS client_blacklist (
    user_id TEXT PRIMARY KEY,
    is_blacklisted BOOLEAN NOT NULL DEFAULT FALSE,
    reason TEXT,
    added_at TIMESTAMPTZ,
    added_by TEXT,
    unblock_requested BOOLEAN NOT NULL DEFAULT FALSE,
    unblock_requested_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS client_unblock_requests (
    id SERIAL PRIMARY KEY,
    user_id TEXT NOT NULL,
    channel_id INTEGER,
    reason TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    status TEXT NOT NULL DEFAULT 'pending',
    decided_at TIMESTAMPTZ,
    decided_by TEXT,
    decision_comment TEXT
);

CREATE INDEX IF NOT EXISTS idx_client_unblock_requests_user ON client_unblock_requests(user_id);

CREATE TABLE IF NOT EXISTS client_usernames (
    id SERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    username TEXT NOT NULL,
    seen_at TIMESTAMPTZ NOT NULL,
    UNIQUE(user_id, username)
);

CREATE TABLE IF NOT EXISTS client_phones (
    id SERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    phone TEXT NOT NULL,
    label TEXT,
    source TEXT NOT NULL CHECK (source IN ('telegram','manual')),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    created_by TEXT
);

CREATE TABLE IF NOT EXISTS client_avatar_history (
    id SERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    fingerprint TEXT NOT NULL,
    source TEXT NOT NULL,
    file_unique_id TEXT,
    file_id TEXT,
    thumb_path TEXT,
    full_path TEXT,
    width INTEGER,
    height INTEGER,
    file_size INTEGER,
    fetched_at TIMESTAMPTZ NOT NULL,
    last_seen_at TIMESTAMPTZ NOT NULL,
    metadata JSONB,
    UNIQUE(user_id, fingerprint)
);

CREATE INDEX IF NOT EXISTS idx_client_avatar_history_user ON client_avatar_history(user_id);
CREATE INDEX IF NOT EXISTS idx_client_avatar_history_last_seen ON client_avatar_history(last_seen_at);

CREATE TABLE IF NOT EXISTS pending_feedback_requests (
    id SERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    channel_id INTEGER NOT NULL,
    ticket_id TEXT,
    source TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    sent_at TIMESTAMPTZ,
    UNIQUE(user_id, channel_id, ticket_id, source)
);

CREATE TABLE IF NOT EXISTS app_settings (
    id SERIAL PRIMARY KEY,
    channel_id INTEGER NOT NULL,
    setting_key TEXT NOT NULL,
    value TEXT NOT NULL,
    UNIQUE(channel_id, setting_key)
);

CREATE TABLE IF NOT EXISTS tasks (
    id SERIAL PRIMARY KEY,
    seq INTEGER NOT NULL,
    source TEXT,
    title TEXT,
    body_html TEXT,
    creator TEXT,
    assignee TEXT,
    tag TEXT,
    status TEXT DEFAULT 'Новая',
    due_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    closed_at TIMESTAMPTZ,
    last_activity_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS task_seq (
    id INTEGER PRIMARY KEY CHECK (id=1),
    val INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS task_links (
    task_id INTEGER NOT NULL,
    ticket_id TEXT NOT NULL,
    PRIMARY KEY(task_id, ticket_id)
);

CREATE TABLE IF NOT EXISTS task_people (
    id SERIAL PRIMARY KEY,
    task_id INTEGER NOT NULL,
    role TEXT NOT NULL,
    identity TEXT NOT NULL,
    UNIQUE(task_id, role, identity)
);

CREATE TABLE IF NOT EXISTS task_comments (
    id SERIAL PRIMARY KEY,
    task_id INTEGER NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    author TEXT,
    html TEXT,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS task_history (
    id SERIAL PRIMARY KEY,
    task_id INTEGER NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    text TEXT
);

CREATE TABLE IF NOT EXISTS ticket_spans (
    id SERIAL PRIMARY KEY,
    ticket_id TEXT NOT NULL,
    span_no INTEGER NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    ended_at TIMESTAMPTZ,
    duration_seconds INTEGER,
    UNIQUE(ticket_id, span_no)
);

CREATE TABLE IF NOT EXISTS ticket_responsibles (
    ticket_id TEXT PRIMARY KEY,
    responsible TEXT NOT NULL,
    assigned_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    assigned_by TEXT
);

CREATE TABLE IF NOT EXISTS ticket_active (
    ticket_id TEXT PRIMARY KEY,
    user_identity TEXT NOT NULL,
    last_seen TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS notifications (
    id SERIAL PRIMARY KEY,
    user_identity TEXT NOT NULL,
    text TEXT NOT NULL,
    url TEXT,
    is_read BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS settings_parameters (
    id SERIAL PRIMARY KEY,
    param_type TEXT NOT NULL,
    value TEXT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    state TEXT NOT NULL DEFAULT 'Активен',
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    extra_json JSONB,
    UNIQUE(param_type, value)
);

CREATE TABLE IF NOT EXISTS it_equipment_catalog (
    id SERIAL PRIMARY KEY,
    item_type TEXT NOT NULL,
    brand TEXT NOT NULL,
    equipment_model TEXT NOT NULL,
    photo_url TEXT,
    serial_number TEXT,
    accessories TEXT,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS knowledge_articles (
    id SERIAL PRIMARY KEY,
    title TEXT NOT NULL,
    department TEXT,
    article_type TEXT,
    status TEXT,
    author TEXT,
    direction TEXT,
    direction_subtype TEXT,
    summary TEXT,
    content TEXT,
    attachments TEXT,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS knowledge_article_files (
    id SERIAL PRIMARY KEY,
    article_id INTEGER REFERENCES knowledge_articles(id) ON DELETE CASCADE,
    draft_token TEXT,
    stored_path TEXT NOT NULL,
    original_name TEXT,
    mime_type TEXT,
    file_size BIGINT,
    uploaded_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_kb_files_article ON knowledge_article_files(article_id);
CREATE INDEX IF NOT EXISTS idx_kb_files_draft ON knowledge_article_files(draft_token);

CREATE TABLE IF NOT EXISTS web_form_sessions (
    id SERIAL PRIMARY KEY,
    token TEXT NOT NULL UNIQUE,
    ticket_id TEXT NOT NULL,
    channel_id INTEGER NOT NULL,
    user_id BIGINT NOT NULL,
    answers_json JSONB,
    client_name TEXT,
    client_contact TEXT,
    username TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    last_active_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS feedbacks (
    id SERIAL PRIMARY KEY,
    user_id BIGINT,
    rating INTEGER,
    timestamp TIMESTAMPTZ,
    ticket_id TEXT,
    channel_id INTEGER REFERENCES channels(id)
);

CREATE OR REPLACE FUNCTION trg_on_ticket_resolved_fn()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.status = 'resolved' THEN
        INSERT INTO pending_feedback_requests(user_id, channel_id, ticket_id, source, created_at, expires_at)
        VALUES (
            NEW.user_id,
            NEW.channel_id,
            NEW.ticket_id,
            CASE WHEN NEW.resolved_by = 'Авто-система' THEN 'auto_close' ELSE 'operator_close' END,
            CURRENT_TIMESTAMP,
            CURRENT_TIMESTAMP + INTERVAL '5 minutes'
        )
        ON CONFLICT DO NOTHING;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_on_ticket_resolved
AFTER UPDATE OF status ON tickets
FOR EACH ROW
EXECUTE FUNCTION trg_on_ticket_resolved_fn();

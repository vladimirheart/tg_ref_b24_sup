-- Baseline schema reproducing the SQLite structure for the legacy support panel.
CREATE TABLE IF NOT EXISTS channels (
    id              BIGSERIAL PRIMARY KEY,
    token           TEXT NOT NULL UNIQUE,
    bot_name        TEXT,
    channel_name    TEXT NOT NULL,
    questions_cfg   TEXT,
    max_questions   INTEGER DEFAULT 0,
    is_active       BOOLEAN DEFAULT TRUE,
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    bot_username    TEXT,
    question_template_id TEXT,
    rating_template_id   TEXT,
    public_id       TEXT
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_channels_public_id ON channels(public_id);

CREATE TABLE IF NOT EXISTS tickets (
    user_id             BIGINT NOT NULL,
    ticket_id           TEXT NOT NULL,
    group_msg_id        BIGINT,
    status              TEXT DEFAULT 'pending',
    resolved_at         TIMESTAMP WITH TIME ZONE,
    resolved_by         TEXT,
    channel_id          BIGINT REFERENCES channels(id),
    reopen_count        INTEGER DEFAULT 0,
    closed_count        INTEGER DEFAULT 0,
    work_time_total_sec INTEGER DEFAULT 0,
    last_reopen_at      TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (user_id, ticket_id)
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_tickets_ticketid_channel
    ON tickets(ticket_id, channel_id);
CREATE INDEX IF NOT EXISTS idx_tickets_channel ON tickets(channel_id);

CREATE TABLE IF NOT EXISTS messages (
    group_msg_id    BIGINT PRIMARY KEY,
    user_id         BIGINT,
    business        TEXT,
    location_type   TEXT,
    city            TEXT,
    location_name   TEXT,
    problem         TEXT,
    created_at      TIMESTAMP WITH TIME ZONE,
    username        TEXT DEFAULT '',
    category        TEXT,
    ticket_id       TEXT UNIQUE,
    created_date    TEXT,
    created_time    TEXT,
    client_name     TEXT,
    client_status   TEXT,
    channel_id      BIGINT REFERENCES channels(id),
    updated_at      TIMESTAMP WITH TIME ZONE,
    updated_by      TEXT
);

CREATE INDEX IF NOT EXISTS idx_messages_channel ON messages(channel_id);

CREATE TABLE IF NOT EXISTS chat_history (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT,
    sender        TEXT,
    message       TEXT,
    timestamp     TIMESTAMP WITH TIME ZONE,
    ticket_id     TEXT,
    message_type  TEXT,
    attachment    TEXT,
    channel_id    BIGINT REFERENCES channels(id),
    tg_message_id BIGINT,
    reply_to_tg_id BIGINT,
    edited_at     TIMESTAMP WITH TIME ZONE,
    deleted_at    TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_history_ticket_channel
    ON chat_history(ticket_id, channel_id);
CREATE INDEX IF NOT EXISTS idx_history_channel_time
    ON chat_history(channel_id, timestamp);

CREATE TABLE IF NOT EXISTS feedbacks (
    id        BIGSERIAL PRIMARY KEY,
    user_id   BIGINT,
    rating    INTEGER,
    timestamp TIMESTAMP WITH TIME ZONE
);

CREATE TABLE IF NOT EXISTS client_statuses (
    user_id    BIGINT PRIMARY KEY,
    status     TEXT DEFAULT 'Не указан',
    updated_at TIMESTAMP WITH TIME ZONE,
    updated_by TEXT
);

CREATE TABLE IF NOT EXISTS client_usernames (
    id        BIGSERIAL PRIMARY KEY,
    user_id   BIGINT NOT NULL,
    username  TEXT NOT NULL,
    seen_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE(user_id, username)
);

CREATE TABLE IF NOT EXISTS client_phones (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL,
    phone       TEXT NOT NULL,
    label       TEXT,
    source      TEXT NOT NULL CHECK (source IN ('telegram', 'manual')),
    is_active   BOOLEAN DEFAULT TRUE,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by  TEXT
);

CREATE TABLE IF NOT EXISTS ticket_spans (
    id               BIGSERIAL PRIMARY KEY,
    ticket_id        TEXT NOT NULL,
    span_no          INTEGER NOT NULL,
    started_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    ended_at         TIMESTAMP WITH TIME ZONE,
    duration_seconds INTEGER,
    UNIQUE(ticket_id, span_no)
);

CREATE TABLE IF NOT EXISTS pending_feedback_requests (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL,
    channel_id  BIGINT NOT NULL,
    ticket_id   TEXT,
    source      TEXT,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    sent_at     TIMESTAMP WITH TIME ZONE,
    UNIQUE(user_id, channel_id, ticket_id, source)
);

CREATE TABLE IF NOT EXISTS app_settings (
    id         BIGSERIAL PRIMARY KEY,
    channel_id BIGINT NOT NULL REFERENCES channels(id),
    key        TEXT NOT NULL,
    value      TEXT NOT NULL,
    UNIQUE(channel_id, key)
);

CREATE TABLE IF NOT EXISTS tasks (
    id               BIGSERIAL PRIMARY KEY,
    seq              BIGINT NOT NULL,
    source           TEXT,
    title            TEXT,
    body_html        TEXT,
    creator          TEXT,
    assignee         TEXT,
    tag              TEXT,
    status           TEXT DEFAULT 'Новая',
    due_at           TIMESTAMP WITH TIME ZONE,
    created_at       TIMESTAMP WITH TIME ZONE,
    closed_at        TIMESTAMP WITH TIME ZONE,
    last_activity_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE IF NOT EXISTS task_seq (
    id  INTEGER PRIMARY KEY CHECK (id = 1),
    val BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS ticket_active (
    ticket_id TEXT PRIMARY KEY,
    "user"    TEXT NOT NULL,
    last_seen TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS task_links (
    task_id   BIGINT NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    ticket_id TEXT   NOT NULL,
    PRIMARY KEY (task_id, ticket_id)
);

CREATE TABLE IF NOT EXISTS task_people (
    id      BIGSERIAL PRIMARY KEY,
    task_id BIGINT REFERENCES tasks(id) ON DELETE CASCADE,
    role    TEXT,
    identity TEXT
);

CREATE TABLE IF NOT EXISTS task_comments (
    id        BIGSERIAL PRIMARY KEY,
    task_id   BIGINT NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    author    TEXT,
    html      TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS task_history (
    id      BIGSERIAL PRIMARY KEY,
    task_id BIGINT NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    at      TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    text    TEXT
);

CREATE TABLE IF NOT EXISTS client_blacklist (
    user_id            TEXT PRIMARY KEY,
    is_blacklisted     BOOLEAN NOT NULL DEFAULT FALSE,
    reason             TEXT,
    added_at           TIMESTAMP WITH TIME ZONE,
    added_by           TEXT,
    unblock_requested  BOOLEAN NOT NULL DEFAULT FALSE,
    unblock_requested_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE IF NOT EXISTS settings_parameters (
    id          BIGSERIAL PRIMARY KEY,
    param_type  TEXT NOT NULL,
    value       TEXT NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    state       TEXT NOT NULL DEFAULT 'Активен',
    is_deleted  BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at  TIMESTAMP WITH TIME ZONE,
    extra_json  TEXT,
    UNIQUE(param_type, value)
);

CREATE TABLE IF NOT EXISTS it_equipment_catalog (
    id                BIGSERIAL PRIMARY KEY,
    equipment_type    TEXT NOT NULL,
    equipment_vendor  TEXT NOT NULL,
    equipment_model   TEXT NOT NULL,
    photo_url         TEXT,
    serial_number     TEXT,
    accessories       TEXT,
    created_at        TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS knowledge_articles (
    id                BIGSERIAL PRIMARY KEY,
    title             TEXT NOT NULL,
    department        TEXT,
    article_type      TEXT,
    status            TEXT,
    author            TEXT,
    direction         TEXT,
    direction_subtype TEXT,
    summary           TEXT,
    content           TEXT,
    created_at        TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    attachments       TEXT
);

CREATE TABLE IF NOT EXISTS knowledge_article_files (
    id             BIGSERIAL PRIMARY KEY,
    article_id     BIGINT REFERENCES knowledge_articles(id) ON DELETE CASCADE,
    draft_token    TEXT,
    stored_path    TEXT NOT NULL,
    original_name  TEXT,
    mime_type      TEXT,
    file_size      BIGINT,
    uploaded_at    TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_kb_files_article
    ON knowledge_article_files(article_id);
CREATE INDEX IF NOT EXISTS idx_kb_files_draft
    ON knowledge_article_files(draft_token);

CREATE TABLE IF NOT EXISTS web_form_sessions (
    id             BIGSERIAL PRIMARY KEY,
    token          TEXT NOT NULL UNIQUE,
    ticket_id      TEXT NOT NULL,
    channel_id     BIGINT NOT NULL,
    user_id        BIGINT NOT NULL,
    answers_json   TEXT,
    client_name    TEXT,
    client_contact TEXT,
    username       TEXT,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    last_active_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS ticket_responsibles (
    ticket_id    TEXT PRIMARY KEY,
    responsible  TEXT NOT NULL,
    assigned_at  TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    assigned_by  TEXT
);

CREATE TABLE IF NOT EXISTS client_unblock_requests (
    id               BIGSERIAL PRIMARY KEY,
    user_id          TEXT NOT NULL,
    channel_id       BIGINT,
    reason           TEXT,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    status           TEXT NOT NULL DEFAULT 'pending',
    decided_at       TIMESTAMP WITH TIME ZONE,
    decided_by       TEXT,
    decision_comment TEXT
);

CREATE INDEX IF NOT EXISTS idx_client_unblock_requests_user
    ON client_unblock_requests(user_id);

CREATE TABLE IF NOT EXISTS client_avatar_history (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT NOT NULL,
    fingerprint    TEXT NOT NULL,
    source         TEXT NOT NULL,
    file_unique_id TEXT,
    file_id        TEXT,
    thumb_path     TEXT,
    full_path      TEXT,
    width          INTEGER,
    height         INTEGER,
    file_size      BIGINT,
    fetched_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    last_seen_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    metadata       TEXT,
    UNIQUE(user_id, fingerprint)
);

CREATE INDEX IF NOT EXISTS idx_client_avatar_history_user
    ON client_avatar_history(user_id);
CREATE INDEX IF NOT EXISTS idx_client_avatar_history_last_seen
    ON client_avatar_history(last_seen_at);

CREATE TABLE IF NOT EXISTS notifications (
    id            BIGSERIAL PRIMARY KEY,
    user_identity TEXT NOT NULL,
    text          TEXT NOT NULL,
    url           TEXT,
    is_read       BOOLEAN DEFAULT FALSE,
    created_at    TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS bot_users (
    user_id       BIGINT PRIMARY KEY,
    username      TEXT,
    first_name    TEXT,
    last_name     TEXT,
    registered_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS bot_chat_history (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT REFERENCES bot_users(user_id),
    message       TEXT,
    timestamp     TIMESTAMP WITH TIME ZONE,
    message_id    BIGINT,
    message_type  TEXT DEFAULT 'text'
);

CREATE TABLE IF NOT EXISTS applications (
    id                 BIGSERIAL PRIMARY KEY,
    user_id            BIGINT REFERENCES bot_users(user_id),
    problem_description TEXT,
    photo_path         TEXT,
    status             TEXT DEFAULT 'new',
    created_at         TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    b24_contact_id     BIGINT,
    b24_deal_id        BIGINT
);

CREATE TABLE IF NOT EXISTS roles (
    id           BIGSERIAL PRIMARY KEY,
    name         TEXT UNIQUE NOT NULL,
    description  TEXT,
    permissions  TEXT NOT NULL DEFAULT '{}'
);

CREATE INDEX IF NOT EXISTS idx_roles_name ON roles(name);

CREATE TABLE IF NOT EXISTS panel_users (
    id                BIGSERIAL PRIMARY KEY,
    username          TEXT NOT NULL UNIQUE,
    password          TEXT,
    password_hash     TEXT,
    role              TEXT,
    role_id           BIGINT REFERENCES roles(id),
    photo             TEXT,
    registration_date TIMESTAMP WITH TIME ZONE,
    birth_date        DATE,
    email             TEXT,
    department        TEXT,
    phones            TEXT,
    full_name         TEXT,
    is_blocked        BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_panel_users_role_id ON panel_users(role_id);

CREATE OR REPLACE FUNCTION trg_ticket_resolved() RETURNS trigger AS $$
BEGIN
    IF NEW.status = 'resolved' THEN
        INSERT INTO pending_feedback_requests (
            user_id, channel_id, ticket_id, source, created_at, expires_at
        ) VALUES (
            NEW.user_id,
            NEW.channel_id,
            NEW.ticket_id,
            CASE WHEN NEW.resolved_by = 'Авто-система' THEN 'auto_close' ELSE 'operator_close' END,
            CURRENT_TIMESTAMP,
            CURRENT_TIMESTAMP + INTERVAL '5 minutes'
        ) ON CONFLICT DO NOTHING;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_on_ticket_resolved ON tickets;
CREATE TRIGGER trg_on_ticket_resolved
    AFTER UPDATE OF status ON tickets
    FOR EACH ROW
    EXECUTE FUNCTION trg_ticket_resolved();

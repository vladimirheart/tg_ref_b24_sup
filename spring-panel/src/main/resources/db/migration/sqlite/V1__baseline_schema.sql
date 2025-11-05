-- Baseline schema reproducing the SQLite structure for the legacy support panel.

CREATE TABLE IF NOT EXISTS users (
    id          INTEGER PRIMARY KEY ${autoIncrement},
    username    VARCHAR(255) NOT NULL UNIQUE,
    password    TEXT NOT NULL,
    enabled     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TEXT DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS user_authorities (
    user_id   BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    authority VARCHAR(100)   NOT NULL,
    PRIMARY KEY (user_id, authority)
);

CREATE INDEX IF NOT EXISTS idx_user_authorities_user ON user_authorities(user_id);

CREATE TABLE IF NOT EXISTS channels (
    id              INTEGER PRIMARY KEY ${autoIncrement},
    token           VARCHAR(255) NOT NULL UNIQUE,
    bot_name        TEXT,
    channel_name    TEXT NOT NULL,
    questions_cfg   TEXT,
    max_questions   INTEGER DEFAULT 0,
    is_active       BOOLEAN DEFAULT TRUE,
    created_at      TEXT DEFAULT CURRENT_TIMESTAMP,
    bot_username    TEXT,
    question_template_id TEXT,
    rating_template_id   TEXT,
    public_id       VARCHAR(255)
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_channels_public_id ON channels(public_id);

CREATE TABLE IF NOT EXISTS tickets (
    user_id             BIGINT NOT NULL,
    ticket_id           VARCHAR(255) NOT NULL,
    group_msg_id        BIGINT,
    status              TEXT DEFAULT 'pending',
    resolved_at         TEXT,
    resolved_by         TEXT,
    channel_id          BIGINT REFERENCES channels(id),
    reopen_count        INTEGER DEFAULT 0,
    closed_count        INTEGER DEFAULT 0,
    work_time_total_sec INTEGER DEFAULT 0,
    last_reopen_at      TEXT,
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
    created_at      TEXT,
    username        VARCHAR(255) DEFAULT '',
    category        TEXT,
    ticket_id       VARCHAR(255) UNIQUE,
    created_date    TEXT,
    created_time    TEXT,
    client_name     TEXT,
    client_status   TEXT,
    channel_id      BIGINT REFERENCES channels(id),
    updated_at      TEXT,
    updated_by      TEXT
);

CREATE INDEX IF NOT EXISTS idx_messages_channel ON messages(channel_id);

CREATE TABLE IF NOT EXISTS chat_history (
    id            INTEGER PRIMARY KEY ${autoIncrement},
    user_id       BIGINT,
    sender        TEXT,
    message       TEXT,
    timestamp     TEXT,
    ticket_id     TEXT,
    message_type  TEXT,
    attachment    TEXT,
    channel_id    BIGINT REFERENCES channels(id),
    tg_message_id BIGINT,
    reply_to_tg_id BIGINT,
    edited_at     TEXT,
    deleted_at    TEXT
);

CREATE INDEX IF NOT EXISTS idx_history_ticket_channel
    ON chat_history(ticket_id, channel_id);
CREATE INDEX IF NOT EXISTS idx_history_channel_time
    ON chat_history(channel_id, timestamp);

CREATE OR REPLACE VIEW client_stats AS
SELECT
    COALESCE(m.username, '') AS username,
    MAX(COALESCE(ch.timestamp, m.created_at)) AS last_contact,
    COUNT(DISTINCT m.ticket_id) AS tickets
FROM messages m
LEFT JOIN chat_history ch ON ch.ticket_id = m.ticket_id
GROUP BY COALESCE(m.username, '');

CREATE TABLE IF NOT EXISTS feedbacks (
    id        INTEGER PRIMARY KEY ${autoIncrement},
    user_id   BIGINT,
    rating    INTEGER,
    timestamp TEXT
);

CREATE TABLE IF NOT EXISTS client_statuses (
    user_id    BIGINT PRIMARY KEY,
    status     TEXT DEFAULT 'Не указан',
    updated_at TEXT,
    updated_by TEXT
);

CREATE TABLE IF NOT EXISTS client_usernames (
    id        INTEGER PRIMARY KEY ${autoIncrement},
    user_id   BIGINT NOT NULL,
    username  VARCHAR(255) NOT NULL,
    seen_at   TEXT NOT NULL,
    UNIQUE(user_id, username)
);

CREATE TABLE IF NOT EXISTS client_phones (
    id          INTEGER PRIMARY KEY ${autoIncrement},
    user_id     BIGINT NOT NULL,
    phone       TEXT NOT NULL,
    label       TEXT,
    source      TEXT NOT NULL CHECK (source IN ('telegram', 'manual')),
    is_active   BOOLEAN DEFAULT TRUE,
    created_at  TEXT NOT NULL,
    created_by  TEXT
);

CREATE TABLE IF NOT EXISTS ticket_spans (
    id               INTEGER PRIMARY KEY ${autoIncrement},
    ticket_id        VARCHAR(255) NOT NULL,
    span_no          INTEGER NOT NULL,
    started_at       TEXT NOT NULL,
    ended_at         TEXT,
    duration_seconds INTEGER,
    UNIQUE(ticket_id, span_no)
);

CREATE TABLE IF NOT EXISTS pending_feedback_requests (
    id          INTEGER PRIMARY KEY ${autoIncrement},
    user_id     BIGINT NOT NULL,
    channel_id  BIGINT NOT NULL,
    ticket_id   VARCHAR(255),
    source      VARCHAR(100),
    created_at  TEXT NOT NULL,
    expires_at  TEXT NOT NULL,
    sent_at     TEXT,
    UNIQUE(user_id, channel_id, ticket_id, source)
);

CREATE TABLE IF NOT EXISTS app_settings (
    id         INTEGER PRIMARY KEY ${autoIncrement},
    channel_id BIGINT NOT NULL REFERENCES channels(id),
    key        VARCHAR(128) NOT NULL,
    value      TEXT NOT NULL,
    UNIQUE(channel_id, key)
);

CREATE TABLE IF NOT EXISTS tasks (
    id               INTEGER PRIMARY KEY ${autoIncrement},
    seq              BIGINT NOT NULL,
    source           TEXT,
    title            TEXT,
    body_html        TEXT,
    creator          TEXT,
    assignee         TEXT,
    tag              TEXT,
    status           TEXT DEFAULT 'Новая',
    due_at           TEXT,
    created_at       TEXT,
    closed_at        TEXT,
    last_activity_at TEXT
);

CREATE TABLE IF NOT EXISTS task_seq (
    id  INTEGER PRIMARY KEY CHECK (id = 1),
    val BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS ticket_active (
    ticket_id VARCHAR(255) PRIMARY KEY,
    user_identity VARCHAR(255) NOT NULL,
    last_seen TEXT DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS task_links (
    task_id   BIGINT NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    ticket_id VARCHAR(255)   NOT NULL,
    PRIMARY KEY (task_id, ticket_id)
);

CREATE TABLE IF NOT EXISTS task_people (
    id      INTEGER PRIMARY KEY ${autoIncrement},
    task_id BIGINT REFERENCES tasks(id) ON DELETE CASCADE,
    role    TEXT,
    identity TEXT
);

CREATE TABLE IF NOT EXISTS task_comments (
    id        INTEGER PRIMARY KEY ${autoIncrement},
    task_id   BIGINT NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    author    TEXT,
    html      TEXT,
    created_at TEXT DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS task_history (
    id      INTEGER PRIMARY KEY ${autoIncrement},
    task_id BIGINT NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    at      TEXT DEFAULT CURRENT_TIMESTAMP,
    text    TEXT
);

CREATE TABLE IF NOT EXISTS client_blacklist (
    user_id            TEXT PRIMARY KEY,
    is_blacklisted     BOOLEAN NOT NULL DEFAULT FALSE,
    reason             TEXT,
    added_at           TEXT,
    added_by           TEXT,
    unblock_requested  BOOLEAN NOT NULL DEFAULT FALSE,
    unblock_requested_at TEXT
);

CREATE TABLE IF NOT EXISTS settings_parameters (
    id          INTEGER PRIMARY KEY ${autoIncrement},
    param_type  TEXT NOT NULL,
    value       TEXT NOT NULL,
    created_at  TEXT DEFAULT CURRENT_TIMESTAMP,
    state       TEXT NOT NULL DEFAULT 'Активен',
    is_deleted  BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at  TEXT,
    extra_json  TEXT,
    UNIQUE(param_type, value)
);

CREATE TABLE IF NOT EXISTS it_equipment_catalog (
    id                INTEGER PRIMARY KEY ${autoIncrement},
    equipment_type    TEXT NOT NULL,
    equipment_vendor  TEXT NOT NULL,
    equipment_model   TEXT NOT NULL,
    photo_url         TEXT,
    serial_number     TEXT,
    accessories       TEXT,
    created_at        TEXT DEFAULT CURRENT_TIMESTAMP,
    updated_at        TEXT DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS knowledge_articles (
    id                INTEGER PRIMARY KEY ${autoIncrement},
    title             TEXT NOT NULL,
    department        TEXT,
    article_type      TEXT,
    status            TEXT,
    author            TEXT,
    direction         TEXT,
    direction_subtype TEXT,
    summary           TEXT,
    content           TEXT,
    created_at        TEXT DEFAULT CURRENT_TIMESTAMP,
    updated_at        TEXT DEFAULT CURRENT_TIMESTAMP,
    attachments       TEXT
);

CREATE TABLE IF NOT EXISTS knowledge_article_files (
    id             INTEGER PRIMARY KEY ${autoIncrement},
    article_id     BIGINT REFERENCES knowledge_articles(id) ON DELETE CASCADE,
    draft_token    VARCHAR(255),
    stored_path    TEXT NOT NULL,
    original_name  TEXT,
    mime_type      TEXT,
    file_size      BIGINT,
    uploaded_at    TEXT DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_kb_files_article
    ON knowledge_article_files(article_id);
CREATE INDEX IF NOT EXISTS idx_kb_files_draft
    ON knowledge_article_files(draft_token);

CREATE TABLE IF NOT EXISTS web_form_sessions (
    id             INTEGER PRIMARY KEY ${autoIncrement},
    token          VARCHAR(255) NOT NULL UNIQUE,
    ticket_id      VARCHAR(255) NOT NULL,
    channel_id     BIGINT NOT NULL,
    user_id        BIGINT NOT NULL,
    answers_json   TEXT,
    client_name    TEXT,
    client_contact TEXT,
    username       TEXT,
    created_at     TEXT NOT NULL,
    last_active_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS ticket_responsibles (
    ticket_id    VARCHAR(255) PRIMARY KEY,
    responsible  TEXT NOT NULL,
    assigned_at  TEXT DEFAULT CURRENT_TIMESTAMP,
    assigned_by  TEXT
);

CREATE TABLE IF NOT EXISTS client_unblock_requests (
    id               INTEGER PRIMARY KEY ${autoIncrement},
    user_id          VARCHAR(255) NOT NULL,
    channel_id       BIGINT,
    reason           TEXT,
    created_at       TEXT NOT NULL,
    status           TEXT NOT NULL DEFAULT 'pending',
    decided_at       TEXT,
    decided_by       TEXT,
    decision_comment TEXT
);

CREATE INDEX IF NOT EXISTS idx_client_unblock_requests_user
    ON client_unblock_requests(user_id);

CREATE TABLE IF NOT EXISTS client_avatar_history (
    id             INTEGER PRIMARY KEY ${autoIncrement},
    user_id        BIGINT NOT NULL,
    fingerprint    VARCHAR(255) NOT NULL,
    source         TEXT NOT NULL,
    file_unique_id TEXT,
    file_id        TEXT,
    thumb_path     TEXT,
    full_path      TEXT,
    width          INTEGER,
    height         INTEGER,
    file_size      BIGINT,
    fetched_at     TEXT NOT NULL,
    last_seen_at   TEXT NOT NULL,
    metadata       TEXT,
    UNIQUE(user_id, fingerprint)
);

CREATE INDEX IF NOT EXISTS idx_client_avatar_history_user
    ON client_avatar_history(user_id);
CREATE INDEX IF NOT EXISTS idx_client_avatar_history_last_seen
    ON client_avatar_history(last_seen_at);

CREATE TABLE IF NOT EXISTS notifications (
    id            INTEGER PRIMARY KEY ${autoIncrement},
    user_identity TEXT NOT NULL,
    text          TEXT NOT NULL,
    url           TEXT,
    is_read       BOOLEAN DEFAULT FALSE,
    created_at    TEXT DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS bot_users (
    user_id       BIGINT PRIMARY KEY,
    username      TEXT,
    first_name    TEXT,
    last_name     TEXT,
    registered_at TEXT DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS bot_chat_history (
    id            INTEGER PRIMARY KEY ${autoIncrement},
    user_id       BIGINT REFERENCES bot_users(user_id),
    message       TEXT,
    timestamp     TEXT,
    message_id    BIGINT,
    message_type  TEXT DEFAULT 'text'
);

CREATE TABLE IF NOT EXISTS applications (
    id                 INTEGER PRIMARY KEY ${autoIncrement},
    user_id            BIGINT REFERENCES bot_users(user_id),
    problem_description TEXT,
    photo_path         TEXT,
    status             TEXT DEFAULT 'new',
    created_at         TEXT DEFAULT CURRENT_TIMESTAMP,
    b24_contact_id     BIGINT,
    b24_deal_id        BIGINT
);

CREATE TABLE IF NOT EXISTS roles (
    id           INTEGER PRIMARY KEY ${autoIncrement},
    name         VARCHAR(255) UNIQUE NOT NULL,
    description  TEXT,
    permissions  TEXT NOT NULL DEFAULT '{}'
);

CREATE INDEX IF NOT EXISTS idx_roles_name ON roles(name);

CREATE TABLE IF NOT EXISTS panel_users (
    id                INTEGER PRIMARY KEY ${autoIncrement},
    username          VARCHAR(255) NOT NULL UNIQUE,
    password          TEXT,
    password_hash     TEXT,
    role              TEXT,
    role_id           BIGINT REFERENCES roles(id),
    photo             TEXT,
    registration_date TEXT,
    birth_date        DATE,
    email             TEXT,
    department        TEXT,
    phones            TEXT,
    full_name         TEXT,
    is_blocked        BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_panel_users_role_id ON panel_users(role_id);

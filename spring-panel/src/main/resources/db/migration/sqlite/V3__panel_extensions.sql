-- Additional schema objects required by the Spring panel UI.

CREATE TABLE IF NOT EXISTS notifications (
    id            INTEGER PRIMARY KEY ${autoIncrement},
    user_identity TEXT,
    text          TEXT,
    url           TEXT,
    is_read       BOOLEAN DEFAULT FALSE,
    created_at    TEXT
);

CREATE TABLE IF NOT EXISTS roles (
    id          INTEGER PRIMARY KEY ${autoIncrement},
    name        VARCHAR(255) NOT NULL UNIQUE,
    permissions TEXT
);

CREATE TABLE IF NOT EXISTS panel_users (
    id                 INTEGER PRIMARY KEY ${autoIncrement},
    username           VARCHAR(255) NOT NULL UNIQUE,
    password_hash      TEXT,
    password           TEXT,
    role               TEXT,
    role_id            BIGINT REFERENCES roles(id),
    photo              TEXT,
    registration_date  TEXT,
    birth_date         TEXT,
    email              TEXT,
    department         TEXT,
    phones             TEXT,
    full_name          TEXT,
    is_blocked         BOOLEAN DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS knowledge_articles (
    id                 INTEGER PRIMARY KEY ${autoIncrement},
    title              TEXT,
    department         TEXT,
    article_type       TEXT,
    status             TEXT,
    author             TEXT,
    direction          TEXT,
    direction_subtype  TEXT,
    summary            TEXT,
    content            TEXT,
    attachments        TEXT,
    created_at         TEXT,
    updated_at         TEXT
);

CREATE TABLE IF NOT EXISTS knowledge_article_files (
    id            INTEGER PRIMARY KEY ${autoIncrement},
    article_id    BIGINT REFERENCES knowledge_articles(id) ON DELETE CASCADE,
    draft_token   TEXT,
    stored_path   TEXT,
    original_name TEXT,
    mime_type     TEXT,
    file_size     BIGINT,
    uploaded_at   TEXT
);

CREATE TABLE IF NOT EXISTS settings_parameters (
    id           INTEGER PRIMARY KEY ${autoIncrement},
    code         VARCHAR(255) NOT NULL UNIQUE,
    description  TEXT,
    default_value TEXT,
    value        TEXT,
    created_at   TEXT,
    updated_at   TEXT
);

CREATE TABLE IF NOT EXISTS it_equipment_catalog (
    id                 INTEGER PRIMARY KEY ${autoIncrement},
    equipment_type     TEXT,
    equipment_vendor   TEXT,
    equipment_model    TEXT,
    photo_url          TEXT,
    serial_number      TEXT,
    accessories        TEXT,
    created_at         TEXT,
    updated_at         TEXT
);

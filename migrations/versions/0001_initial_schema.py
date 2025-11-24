"""Initial SQLite schema managed by Alembic."""
from __future__ import annotations

from alembic import op
import sqlalchemy as sa

revision = "0001_initial_schema"
down_revision = None
branch_labels = None
depends_on = None

TABLE_STATEMENTS: list[tuple[str, str]] = [
    (
        "bot_credentials",
        """
        CREATE TABLE IF NOT EXISTS bot_credentials (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL,
            platform TEXT NOT NULL DEFAULT 'telegram',
            encrypted_token TEXT NOT NULL,
            metadata TEXT DEFAULT '{}',
            is_active INTEGER NOT NULL DEFAULT 1,
            created_at TEXT NOT NULL DEFAULT (datetime('now')),
            updated_at TEXT NOT NULL DEFAULT (datetime('now'))
        )
        """,
    ),
    (
        "channels",
        """
        CREATE TABLE IF NOT EXISTS channels (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            token TEXT NOT NULL UNIQUE,
            bot_name TEXT,
            bot_username TEXT,
            channel_name TEXT NOT NULL,
            questions_cfg TEXT,
            max_questions INTEGER DEFAULT 0,
            is_active INTEGER DEFAULT 1,
            question_template_id TEXT,
            rating_template_id TEXT,
            auto_action_template_id TEXT,
            public_id TEXT,
            description TEXT,
            filters TEXT DEFAULT '{}',
            delivery_settings TEXT DEFAULT '{}',
            platform TEXT NOT NULL DEFAULT 'telegram',
            platform_config TEXT DEFAULT '{}',
            credential_id INTEGER REFERENCES bot_credentials(id),
            created_at TEXT DEFAULT (datetime('now')),
            updated_at TEXT DEFAULT (datetime('now')),
            support_chat_id TEXT
        )
        """,
    ),
    (
        "channel_notifications",
        """
        CREATE TABLE IF NOT EXISTS channel_notifications (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            channel_id INTEGER NOT NULL REFERENCES channels(id) ON DELETE CASCADE,
            recipient TEXT,
            payload TEXT NOT NULL,
            status TEXT NOT NULL DEFAULT 'pending',
            error TEXT,
            attempts INTEGER NOT NULL DEFAULT 0,
            scheduled_at TEXT NOT NULL DEFAULT (datetime('now')),
            created_at TEXT NOT NULL DEFAULT (datetime('now')),
            started_at TEXT,
            finished_at TEXT
        )
        """,
    ),
    (
        "chat_history",
        """
        CREATE TABLE IF NOT EXISTS chat_history (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id INTEGER,
            sender TEXT,
            message TEXT,
            timestamp TEXT,
            ticket_id TEXT,
            message_type TEXT,
            attachment TEXT,
            channel_id INTEGER REFERENCES channels(id),
            tg_message_id INTEGER,
            reply_to_tg_id INTEGER,
            edited_at TEXT,
            deleted_at TEXT
        )
        """,
    ),
    (
        "tickets",
        """
        CREATE TABLE IF NOT EXISTS tickets (
            user_id INTEGER,
            group_msg_id INTEGER,
            status TEXT DEFAULT 'pending',
            resolved_at TEXT,
            resolved_by TEXT,
            ticket_id TEXT UNIQUE,
            channel_id INTEGER REFERENCES channels(id),
            reopen_count INTEGER DEFAULT 0,
            closed_count INTEGER DEFAULT 0,
            work_time_total_sec INTEGER DEFAULT 0,
            last_reopen_at TEXT,
            PRIMARY KEY (user_id, ticket_id)
        )
        """,
    ),
    (
        "messages",
        """
        CREATE TABLE IF NOT EXISTS messages (
            group_msg_id INTEGER PRIMARY KEY,
            user_id INTEGER,
            business TEXT,
            location_type TEXT,
            city TEXT,
            location_name TEXT,
            problem TEXT,
            created_at TEXT,
            username TEXT DEFAULT '',
            category TEXT,
            ticket_id TEXT UNIQUE,
            created_date TEXT,
            created_time TEXT,
            client_name TEXT,
            client_status TEXT,
            channel_id INTEGER REFERENCES channels(id),
            updated_at TEXT,
            updated_by TEXT
        )
        """,
    ),
    (
        "client_statuses",
        """
        CREATE TABLE IF NOT EXISTS client_statuses (
            user_id INTEGER PRIMARY KEY,
            status TEXT DEFAULT 'Не указан',
            updated_at TEXT,
            updated_by TEXT
        )
        """,
    ),
    (
        "client_blacklist",
        """
        CREATE TABLE IF NOT EXISTS client_blacklist (
            user_id TEXT PRIMARY KEY,
            is_blacklisted INTEGER NOT NULL DEFAULT 0,
            reason TEXT,
            added_at TEXT,
            added_by TEXT,
            unblock_requested INTEGER NOT NULL DEFAULT 0,
            unblock_requested_at TEXT
        )
        """,
    ),
    (
        "client_unblock_requests",
        """
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
        )
        """,
    ),
    (
        "client_usernames",
        """
        CREATE TABLE IF NOT EXISTS client_usernames (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id INTEGER NOT NULL,
            username TEXT NOT NULL,
            seen_at TEXT NOT NULL,
            UNIQUE(user_id, username)
        )
        """,
    ),
    (
        "client_phones",
        """
        CREATE TABLE IF NOT EXISTS client_phones (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id INTEGER NOT NULL,
            phone TEXT NOT NULL,
            label TEXT,
            source TEXT NOT NULL CHECK (source IN ('telegram','manual')),
            is_active INTEGER DEFAULT 1,
            created_at TEXT NOT NULL,
            created_by TEXT
        )
        """,
    ),
    (
        "client_avatar_history",
        """
        CREATE TABLE IF NOT EXISTS client_avatar_history (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id INTEGER NOT NULL,
            fingerprint TEXT NOT NULL,
            source TEXT NOT NULL,
            file_unique_id TEXT,
            file_id TEXT,
            thumb_path TEXT,
            full_path TEXT,
            width INTEGER,
            height INTEGER,
            file_size INTEGER,
            fetched_at TEXT NOT NULL,
            last_seen_at TEXT NOT NULL,
            metadata TEXT,
            UNIQUE(user_id, fingerprint)
        )
        """,
    ),
    (
        "pending_feedback_requests",
        """
        CREATE TABLE IF NOT EXISTS pending_feedback_requests (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id INTEGER NOT NULL,
            channel_id INTEGER NOT NULL,
            ticket_id TEXT,
            source TEXT,
            created_at TEXT NOT NULL,
            expires_at TEXT NOT NULL,
            sent_at TEXT,
            UNIQUE(user_id, channel_id, ticket_id, source)
        )
        """,
    ),
    (
        "app_settings",
        """
        CREATE TABLE IF NOT EXISTS app_settings (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            channel_id INTEGER NOT NULL,
            key TEXT NOT NULL,
            value TEXT NOT NULL,
            UNIQUE(channel_id, key)
        )
        """,
    ),
    (
        "tasks",
        """
        CREATE TABLE IF NOT EXISTS tasks (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            seq INTEGER NOT NULL,
            source TEXT,
            title TEXT,
            body_html TEXT,
            creator TEXT,
            assignee TEXT,
            tag TEXT,
            status TEXT DEFAULT 'Новая',
            due_at TEXT,
            created_at TEXT DEFAULT (datetime('now')),
            closed_at TEXT,
            last_activity_at TEXT DEFAULT (datetime('now'))
        )
        """,
    ),
    (
        "task_seq",
        """
        CREATE TABLE IF NOT EXISTS task_seq (
            id INTEGER PRIMARY KEY CHECK (id=1),
            val INTEGER NOT NULL
        )
        """,
    ),
    (
        "task_links",
        """
        CREATE TABLE IF NOT EXISTS task_links (
            task_id INTEGER NOT NULL,
            ticket_id TEXT NOT NULL,
            PRIMARY KEY(task_id, ticket_id)
        )
        """,
    ),
    (
        "task_people",
        """
        CREATE TABLE IF NOT EXISTS task_people (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            task_id INTEGER NOT NULL,
            role TEXT NOT NULL,
            identity TEXT NOT NULL,
            UNIQUE(task_id, role, identity)
        )
        """,
    ),
    (
        "task_comments",
        """
        CREATE TABLE IF NOT EXISTS task_comments (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            task_id INTEGER NOT NULL,
            author TEXT,
            html TEXT,
            created_at TEXT DEFAULT (datetime('now')),
            FOREIGN KEY(task_id) REFERENCES tasks(id) ON DELETE CASCADE
        )
        """,
    ),
    (
        "task_history",
        """
        CREATE TABLE IF NOT EXISTS task_history (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            task_id INTEGER NOT NULL,
            at TEXT DEFAULT (datetime('now')),
            text TEXT,
            FOREIGN KEY(task_id) REFERENCES tasks(id) ON DELETE CASCADE
        )
        """,
    ),
    (
        "ticket_spans",
        """
        CREATE TABLE IF NOT EXISTS ticket_spans (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            ticket_id TEXT NOT NULL,
            span_no INTEGER NOT NULL,
            started_at TEXT NOT NULL,
            ended_at TEXT,
            duration_seconds INTEGER,
            UNIQUE(ticket_id, span_no)
        )
        """,
    ),
    (
        "ticket_responsibles",
        """
        CREATE TABLE IF NOT EXISTS ticket_responsibles (
            ticket_id TEXT PRIMARY KEY,
            responsible TEXT NOT NULL,
            assigned_at TEXT DEFAULT (datetime('now')),
            assigned_by TEXT
        )
        """,
    ),
    (
        "ticket_active",
        """
        CREATE TABLE IF NOT EXISTS ticket_active (
            ticket_id TEXT PRIMARY KEY,
            user TEXT NOT NULL,
            last_seen TEXT DEFAULT (datetime('now'))
        )
        """,
    ),
    (
        "notifications",
        """
        CREATE TABLE IF NOT EXISTS notifications (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            user TEXT NOT NULL,
            text TEXT NOT NULL,
            url TEXT,
            is_read INTEGER DEFAULT 0,
            created_at TEXT DEFAULT (datetime('now'))
        )
        """,
    ),
    (
        "settings_parameters",
        """
        CREATE TABLE IF NOT EXISTS settings_parameters (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            param_type TEXT NOT NULL,
            value TEXT NOT NULL,
            created_at TEXT DEFAULT (datetime('now')),
            state TEXT NOT NULL DEFAULT 'Активен',
            is_deleted INTEGER NOT NULL DEFAULT 0,
            deleted_at TEXT,
            extra_json TEXT,
            UNIQUE(param_type, value)
        )
        """,
    ),
    (
        "it_equipment_catalog",
        """
        CREATE TABLE IF NOT EXISTS it_equipment_catalog (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            equipment_type TEXT NOT NULL,
            equipment_vendor TEXT NOT NULL,
            equipment_model TEXT NOT NULL,
            photo_url TEXT,
            serial_number TEXT,
            accessories TEXT,
            created_at TEXT DEFAULT (datetime('now')),
            updated_at TEXT DEFAULT (datetime('now'))
        )
        """,
    ),
    (
        "knowledge_articles",
        """
        CREATE TABLE IF NOT EXISTS knowledge_articles (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
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
            created_at TEXT DEFAULT (datetime('now')),
            updated_at TEXT DEFAULT (datetime('now'))
        )
        """,
    ),
    (
        "knowledge_article_files",
        """
        CREATE TABLE IF NOT EXISTS knowledge_article_files (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            article_id INTEGER,
            draft_token TEXT,
            stored_path TEXT NOT NULL,
            original_name TEXT,
            mime_type TEXT,
            file_size INTEGER,
            uploaded_at TEXT DEFAULT (datetime('now')),
            FOREIGN KEY(article_id) REFERENCES knowledge_articles(id) ON DELETE CASCADE
        )
        """,
    ),
    (
        "web_form_sessions",
        """
        CREATE TABLE IF NOT EXISTS web_form_sessions (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            token TEXT NOT NULL UNIQUE,
            ticket_id TEXT NOT NULL,
            channel_id INTEGER NOT NULL,
            user_id INTEGER NOT NULL,
            answers_json TEXT,
            client_name TEXT,
            client_contact TEXT,
            username TEXT,
            created_at TEXT NOT NULL,
            last_active_at TEXT NOT NULL
        )
        """,
    ),
    (
        "feedbacks",
        """
        CREATE TABLE IF NOT EXISTS feedbacks (
            user_id INTEGER,
            rating INTEGER,
            timestamp TEXT
        )
        """,
    ),
]

INDEX_STATEMENTS = [
    "CREATE UNIQUE INDEX IF NOT EXISTS idx_channels_public_id ON channels(public_id)",
    "CREATE INDEX IF NOT EXISTS idx_client_avatar_history_user ON client_avatar_history(user_id)",
    "CREATE INDEX IF NOT EXISTS idx_client_avatar_history_last_seen ON client_avatar_history(last_seen_at)",
    "CREATE INDEX IF NOT EXISTS idx_client_unblock_requests_user ON client_unblock_requests(user_id)",
    "CREATE INDEX IF NOT EXISTS idx_history_channel_time ON chat_history(channel_id, timestamp)",
    "CREATE INDEX IF NOT EXISTS idx_history_ticket_channel ON chat_history(ticket_id, channel_id)",
    "CREATE INDEX IF NOT EXISTS idx_kb_files_article ON knowledge_article_files(article_id)",
    "CREATE INDEX IF NOT EXISTS idx_kb_files_draft ON knowledge_article_files(draft_token)",
    "CREATE INDEX IF NOT EXISTS idx_messages_channel ON messages(channel_id)",
    "CREATE INDEX IF NOT EXISTS idx_tickets_channel ON tickets(channel_id)",
    "CREATE UNIQUE INDEX IF NOT EXISTS ux_tickets_ticketid_channel ON tickets(ticket_id, channel_id)",
]

COLUMN_REQUIREMENTS = {
    "channels": [
        ("bot_name", "TEXT"),
        ("bot_username", "TEXT"),
        ("channel_name", "TEXT"),
        ("questions_cfg", "TEXT"),
        ("max_questions", "INTEGER DEFAULT 0"),
        ("is_active", "INTEGER DEFAULT 1"),
        ("question_template_id", "TEXT"),
        ("rating_template_id", "TEXT"),
        ("auto_action_template_id", "TEXT"),
        ("public_id", "TEXT"),
        ("platform", "TEXT NOT NULL DEFAULT 'telegram'"),
        ("platform_config", "TEXT"),
        ("credential_id", "INTEGER"),
        ("description", "TEXT"),
        ("filters", "TEXT DEFAULT '{}'"),
        ("delivery_settings", "TEXT DEFAULT '{}'"),
        ("updated_at", "TEXT DEFAULT (datetime('now'))"),
        ("support_chat_id", "TEXT"),
    ],
    "chat_history": [
        ("ticket_id", "TEXT"),
        ("message_type", "TEXT"),
        ("attachment", "TEXT"),
        ("channel_id", "INTEGER"),
        ("tg_message_id", "INTEGER"),
        ("reply_to_tg_id", "INTEGER"),
        ("edited_at", "TEXT"),
        ("deleted_at", "TEXT"),
    ],
    "tickets": [
        ("channel_id", "INTEGER"),
        ("reopen_count", "INTEGER DEFAULT 0"),
        ("closed_count", "INTEGER DEFAULT 0"),
        ("work_time_total_sec", "INTEGER DEFAULT 0"),
        ("last_reopen_at", "TEXT"),
    ],
    "messages": [
        ("created_date", "TEXT"),
        ("created_time", "TEXT"),
        ("client_name", "TEXT"),
        ("client_status", "TEXT"),
        ("channel_id", "INTEGER"),
        ("updated_at", "TEXT"),
        ("updated_by", "TEXT"),
    ],
}


def _table_exists(conn, name: str) -> bool:
    query = sa.text("SELECT name FROM sqlite_master WHERE type='table' AND name=:name")
    return conn.execute(query, {"name": name}).fetchone() is not None


def _column_exists(conn, table: str, column: str) -> bool:
    pragma = sa.text(f"PRAGMA table_info({table})")
    rows = conn.execute(pragma).fetchall()
    return any(row[1] == column for row in rows)


def _add_column(conn, table: str, column: str, ddl: str) -> None:
    """Adds a column, handling SQLite limitations for dynamic defaults.

    SQLite does not allow adding a column with a non-constant default such as
    ``datetime('now')``. For such cases we add the column without the default
    and backfill existing rows manually.
    """

    default_now = "DEFAULT (datetime('now'))"
    needs_backfill = default_now in ddl
    ddl_sql = ddl.replace(default_now, "").strip() if needs_backfill else ddl
    op.execute(f"ALTER TABLE {table} ADD COLUMN {column} {ddl_sql}")
    if needs_backfill:
        op.execute(sa.text(f"UPDATE {table} SET {column} = datetime('now') WHERE {column} IS NULL"))


def upgrade() -> None:
    conn = op.get_bind()
    for name, statement in TABLE_STATEMENTS:
        if not _table_exists(conn, name):
            op.execute(statement)
    for table, columns in COLUMN_REQUIREMENTS.items():
        if not _table_exists(conn, table):
            continue
        for column, ddl in columns:
            if not _column_exists(conn, table, column):
                _add_column(conn, table, column, ddl)
    for statement in INDEX_STATEMENTS:
        op.execute(statement)
    op.execute(
        """
        CREATE TRIGGER IF NOT EXISTS trg_on_ticket_resolved
        AFTER UPDATE OF status ON tickets
        WHEN NEW.status = 'resolved'
        BEGIN
            INSERT OR IGNORE INTO pending_feedback_requests(
                user_id, channel_id, ticket_id, source, created_at, expires_at
            )
            VALUES(
                NEW.user_id,
                NEW.channel_id,
                NEW.ticket_id,
                CASE WHEN NEW.resolved_by = 'Авто-система' THEN 'auto_close' ELSE 'operator_close' END,
                datetime('now'),
                datetime('now', '+5 minutes')
            );
        END
        """
    )


def downgrade() -> None:
    raise RuntimeError("Downgrade is not supported for the initial schema")

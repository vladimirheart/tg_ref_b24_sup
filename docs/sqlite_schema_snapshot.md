# SQLite schema snapshot

This document captures the structure of the legacy SQLite databases shipped with the monorepo. The dumps were produced with `sqlite3 .schema` on the reference data files committed to the repository.

## `tickets.db`

```sql
CREATE TABLE chat_history (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER,
                sender TEXT,
                message TEXT,
                timestamp TEXT
            , ticket_id TEXT, message_type TEXT, attachment TEXT, channel_id INTEGER REFERENCES channels(id), tg_message_id INTEGER, reply_to_tg_id INTEGER, edited_at TEXT, deleted_at TEXT);
CREATE TABLE sqlite_sequence(name,seq);
CREATE TABLE feedbacks (
                user_id INTEGER,
                rating INTEGER,
                timestamp TEXT
            );
CREATE TABLE IF NOT EXISTS "tickets" (
        user_id INTEGER,
        group_msg_id INTEGER,
        status TEXT DEFAULT 'pending',
        resolved_at TEXT,
        resolved_by TEXT,
        ticket_id TEXT UNIQUE, channel_id INTEGER REFERENCES channels(id), reopen_count INTEGER DEFAULT 0, closed_count INTEGER DEFAULT 0, work_time_total_sec INTEGER DEFAULT 0, last_reopen_at TEXT,
        PRIMARY KEY (user_id, ticket_id)
    );
CREATE TABLE IF NOT EXISTS "messages" (
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
        ticket_id TEXT UNIQUE
    , created_date TEXT, created_time TEXT, client_name TEXT, client_status TEXT, channel_id INTEGER REFERENCES channels(id), updated_at TEXT, updated_by TEXT);
CREATE TABLE client_statuses (
                user_id INTEGER PRIMARY KEY,
                status TEXT DEFAULT 'Не указан',
                updated_at TEXT,
                updated_by TEXT
            );
CREATE TABLE channels (
  id            INTEGER PRIMARY KEY AUTOINCREMENT,
  token         TEXT NOT NULL UNIQUE,
  bot_name      TEXT,              -- ?????????? ???? getMe()
  channel_name  TEXT NOT NULL,     -- ???????? ?????????????? (???????????????????????? ?????? ???????? ????????)
  questions_cfg TEXT,              -- JSON ?? ?????????????????????? ???????????????? ?????? ?????????? ????????????
  max_questions INTEGER DEFAULT 0, -- ??????????????????????
  is_active     INTEGER DEFAULT 1, -- 1=??????????????
  created_at    TEXT DEFAULT (datetime('now'))
, bot_username TEXT, question_template_id TEXT, rating_template_id TEXT, public_id TEXT);
CREATE INDEX idx_tickets_channel ON tickets(channel_id);
CREATE INDEX idx_messages_channel ON messages(channel_id);
CREATE INDEX idx_history_ticket_channel ON chat_history(ticket_id, channel_id);
CREATE INDEX idx_history_channel_time ON chat_history(channel_id, timestamp);
CREATE UNIQUE INDEX ux_tickets_ticketid_channel
ON tickets(ticket_id, channel_id);
CREATE TABLE client_usernames (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER NOT NULL,
                username TEXT NOT NULL,
                seen_at TEXT NOT NULL,
                UNIQUE(user_id, username)
            );
CREATE TABLE client_phones (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER NOT NULL,
                phone TEXT NOT NULL,
                label TEXT,
                source TEXT NOT NULL CHECK (source IN ('telegram','manual')),
                is_active INTEGER DEFAULT 1,
                created_at TEXT NOT NULL,
                created_by TEXT
            );
CREATE TABLE ticket_spans (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                ticket_id TEXT NOT NULL,
                span_no INTEGER NOT NULL,
                started_at TEXT NOT NULL,
                ended_at   TEXT,
                duration_seconds INTEGER,
                UNIQUE(ticket_id, span_no)
            );
CREATE TABLE pending_feedback_requests (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER NOT NULL,
                channel_id INTEGER NOT NULL,
                ticket_id TEXT,
                source TEXT, -- 'operator_close' | 'auto_close'
                created_at TEXT NOT NULL,
                expires_at TEXT NOT NULL, sent_at TEXT,
                UNIQUE(user_id, channel_id, ticket_id, source)
            );
CREATE TABLE app_settings(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                channel_id INTEGER NOT NULL,
                key TEXT NOT NULL,
                value TEXT NOT NULL,
                UNIQUE(channel_id, key)
            );
CREATE TRIGGER trg_on_ticket_resolved
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
            END;
CREATE TABLE tasks(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            seq INTEGER NOT NULL,               -- порядковый номер (целый, инкремент)
            source TEXT,                        -- 'DL' или NULL
            title TEXT,
            body_html TEXT,
            creator TEXT,
            assignee TEXT,
            tag TEXT,
            status TEXT DEFAULT 'Новая',
            due_at TEXT,                        -- ISO
            created_at TEXT DEFAULT (datetime('now')),
            closed_at TEXT,                     -- ISO
            last_activity_at TEXT DEFAULT (datetime('now'))
        );
CREATE TABLE task_people(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            task_id INTEGER NOT NULL,
            role TEXT NOT NULL,                 -- 'co' или 'watcher'
            identity TEXT NOT NULL,
            UNIQUE(task_id, role, identity),
            FOREIGN KEY(task_id) REFERENCES tasks(id) ON DELETE CASCADE
        );
CREATE TABLE task_comments(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            task_id INTEGER NOT NULL,
            author TEXT,
            html TEXT,
            created_at TEXT DEFAULT (datetime('now')),
            FOREIGN KEY(task_id) REFERENCES tasks(id) ON DELETE CASCADE
        );
CREATE TABLE task_history(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            task_id INTEGER NOT NULL,
            at TEXT DEFAULT (datetime('now')),
            text TEXT,
            FOREIGN KEY(task_id) REFERENCES tasks(id) ON DELETE CASCADE
        );
CREATE TABLE notifications(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            user TEXT NOT NULL,
            text TEXT NOT NULL,
            url TEXT,
            is_read INTEGER DEFAULT 0,
            created_at TEXT DEFAULT (datetime('now'))
        );
CREATE TABLE task_seq(
            id INTEGER PRIMARY KEY CHECK (id=1),
            val INTEGER NOT NULL
        );
CREATE TABLE ticket_active(
            ticket_id TEXT PRIMARY KEY,
            user      TEXT NOT NULL,
            last_seen TEXT DEFAULT (datetime('now'))
        );
CREATE TABLE task_links(
            task_id   INTEGER NOT NULL,
            ticket_id TEXT    NOT NULL,
            PRIMARY KEY(task_id, ticket_id)
        );
CREATE TABLE client_blacklist (
                user_id TEXT PRIMARY KEY,
                is_blacklisted INTEGER NOT NULL DEFAULT 0,
                reason TEXT,
                added_at TEXT,
                added_by TEXT,
                unblock_requested INTEGER NOT NULL DEFAULT 0,
                unblock_requested_at TEXT
            );
CREATE TABLE settings_parameters (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    param_type TEXT NOT NULL,
                    value TEXT NOT NULL,
                    created_at TEXT DEFAULT (datetime('now')), state TEXT NOT NULL DEFAULT 'Активен', is_deleted INTEGER NOT NULL DEFAULT 0, deleted_at TEXT, extra_json TEXT,
                    UNIQUE(param_type, value)
                );
CREATE TABLE it_equipment_catalog (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    equipment_type TEXT NOT NULL,
                    equipment_vendor TEXT NOT NULL,
                    equipment_model TEXT NOT NULL,
                    photo_url TEXT,
                    serial_number TEXT,
                    accessories TEXT,
                    created_at TEXT DEFAULT (datetime('now')),
                    updated_at TEXT DEFAULT (datetime('now'))
                );
CREATE TABLE knowledge_articles (
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
                created_at TEXT DEFAULT (datetime('now')),
                updated_at TEXT DEFAULT (datetime('now'))
            , attachments TEXT);
CREATE TABLE knowledge_article_files (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                article_id INTEGER,
                draft_token TEXT,
                stored_path TEXT NOT NULL,
                original_name TEXT,
                mime_type TEXT,
                file_size INTEGER,
                uploaded_at TEXT DEFAULT (datetime('now')),
                FOREIGN KEY(article_id) REFERENCES knowledge_articles(id) ON DELETE CASCADE
            );
CREATE INDEX idx_kb_files_article ON knowledge_article_files(article_id);
CREATE INDEX idx_kb_files_draft ON knowledge_article_files(draft_token);
CREATE TABLE web_form_sessions (
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
        );
CREATE UNIQUE INDEX idx_channels_public_id ON channels(public_id);
CREATE TABLE ticket_responsibles(
            ticket_id   TEXT PRIMARY KEY,
            responsible TEXT NOT NULL,
            assigned_at TEXT DEFAULT (datetime('now')),
            assigned_by TEXT
        );
CREATE TABLE client_unblock_requests (
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
CREATE INDEX idx_client_unblock_requests_user
                ON client_unblock_requests(user_id)
                ;
CREATE TABLE client_avatar_history (
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
            );
CREATE INDEX idx_client_avatar_history_user ON client_avatar_history(user_id);
CREATE INDEX idx_client_avatar_history_last_seen ON client_avatar_history(last_seen_at);
```

## `users.db`

```sql
CREATE TABLE users (
        id INTEGER PRIMARY KEY,
        username TEXT UNIQUE,
        password TEXT,
        role TEXT DEFAULT 'admin'
    , password_hash VARCHAR(255), role_id INTEGER, photo TEXT, registration_date TEXT, birth_date TEXT, email TEXT, department TEXT, phones TEXT, full_name TEXT, is_blocked INTEGER NOT NULL DEFAULT 0);
CREATE TABLE roles (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT UNIQUE NOT NULL,
                description TEXT,
                permissions TEXT NOT NULL DEFAULT '{}'
            );
CREATE TABLE sqlite_sequence(name,seq);
CREATE UNIQUE INDEX idx_roles_name ON roles(name);
CREATE INDEX idx_users_role_id ON users(role_id);
```

## `bot_database.db`

```sql
CREATE TABLE chat_history (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER,
                message TEXT,
                timestamp INTEGER,
                message_id INTEGER,
                message_type TEXT DEFAULT 'text',
                FOREIGN KEY (user_id) REFERENCES users (user_id)
            );
CREATE TABLE sqlite_sequence(name,seq);
CREATE TABLE users (
                user_id INTEGER PRIMARY KEY,
                username TEXT,
                first_name TEXT,
                last_name TEXT,
                registered_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            );
CREATE TABLE applications (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER,
                problem_description TEXT,
                photo_path TEXT,
                status TEXT DEFAULT 'new',
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                b24_contact_id INTEGER,
                b24_deal_id INTEGER,
                FOREIGN KEY (user_id) REFERENCES users (user_id)
            );
```

# JPA entity mapping

The following tables summarize how the legacy tables align with the new JPA entities.

## Spring Panel (`spring-panel`)

| SQLite table | Purpose | JPA entities |
| --- | --- | --- |
| `channels` | Telegram channel metadata | `Channel`, `AppSetting` (FK) |
| `tickets` | Ticket status and resolution info | `Ticket`, `TicketSpan`, `TicketActive`, `TicketResponsible` |
| `messages` | Original ticket payload | `Message` |
| `chat_history` | Conversation history | `ChatHistory`, `BotChatHistory` (structure reused) |
| `feedbacks` | Customer feedback (no PK in SQLite) | `Feedback` (adds surrogate key) |
| `client_statuses` | Saved client status | `ClientStatus` |
| `client_usernames` | Username history | `ClientUsername` |
| `client_phones` | Phone book | `ClientPhone` |
| `pending_feedback_requests` | Scheduled rating prompts | `PendingFeedbackRequest` |
| `app_settings` | Per-channel app flags | `AppSetting` |
| `tasks` / `task_seq` / `task_people` / `task_comments` / `task_history` / `task_links` | CRM tasks | `Task`, `TaskSequence`, `TaskPerson`, `TaskComment`, `TaskHistory`, `TaskLink` |
| `ticket_active` | Currently active tickets | `TicketActive` |
| `client_blacklist` | Blacklist registry | `ClientBlacklist` |
| `settings_parameters` | Parameter directory | `SettingsParameter` |
| `it_equipment_catalog` | IT asset catalog | `ItEquipmentCatalog` |
| `knowledge_articles` / `knowledge_article_files` | Knowledge base | `KnowledgeArticle`, `KnowledgeArticleFile` |
| `web_form_sessions` | External web-forms | `WebFormSession` |
| `ticket_responsibles` | Assigned operators | `TicketResponsible` |
| `client_unblock_requests` | Unblock workflow | `ClientUnblockRequest` |
| `client_avatar_history` | Avatar cache | `ClientAvatarHistory` |
| `notifications` | Panel notifications | `Notification` |
| `panel_users` (migrated from `users.db`) | Panel accounts | `PanelUser` |
| `roles` (from `users.db`) | Role catalogue | `Role` |
| `bot_users` / `bot_chat_history` / `applications` (from `bot_database.db`) | Telegram bot data reused in the panel | `BotUser`, `BotChatHistory`, `ApplicationRecord` |

## Java Bot (`java-bot`)

| SQLite table | Purpose | JPA entities |
| --- | --- | --- |
| `channels` | Telegram channels the bot may post to | `Channel` |
| `client_blacklist` | Blacklisted clients | `ClientBlacklist` |
| `client_unblock_requests` | Requests to unblock | `ClientUnblockRequest` |
| `chat_history` | Message audit trail | (accessed via repositories for analytics) |
| `users` | Bot user directory | reused by services (maps to `BotUser` DTOs) |
| `applications` | Submitted applications | reused alongside `ApplicationRecord` in the panel |

### Known differences

- The historical `feedbacks` table in SQLite does not have a primary key. The Spring entity introduces an auto-increment surrogate key so that JPA can manage the table.
- The Java and Spring stacks normalise certain column names (`user_identity` vs `user`) but keep the payload compatible with SQLite.

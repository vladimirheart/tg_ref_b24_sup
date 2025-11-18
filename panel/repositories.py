"""Repositories encapsulating access to channel related tables."""

from __future__ import annotations

import json
import secrets
import sqlite3
from contextlib import contextmanager
from dataclasses import asdict
from pathlib import Path
from typing import Iterator

from .models import BotCredential, Channel, ChannelNotification
from .secrets import decrypt_token, encrypt_token, mask_token, SecretStorageError

BASE_DIR = Path(__file__).resolve().parent.parent
DB_PATH = BASE_DIR / "tickets.db"


def _create_connection() -> sqlite3.Connection:
    conn = sqlite3.connect(DB_PATH, timeout=30, isolation_level=None, check_same_thread=False)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA journal_mode=WAL;")
    conn.execute("PRAGMA busy_timeout=30000;")
    conn.execute("PRAGMA synchronous=NORMAL;")
    conn.execute("PRAGMA foreign_keys=ON;")
    return conn


@contextmanager
def get_connection() -> Iterator[sqlite3.Connection]:
    conn = _create_connection()
    try:
        yield conn
    finally:
        conn.close()


def _serialize_metadata(value) -> str:
    if isinstance(value, str):
        return value
    try:
        return json.dumps(value or {})
    except TypeError:
        return "{}"


def _parse_json(value) -> dict:
    if not value:
        return {}
    if isinstance(value, dict):
        return value
    try:
        parsed = json.loads(value)
        return parsed if isinstance(parsed, dict) else {}
    except (TypeError, json.JSONDecodeError):
        return {}


class BotCredentialRepository:
    table = "bot_credentials"

    def list(self) -> list[BotCredential]:
        with get_connection() as conn:
            rows = conn.execute(
                f"SELECT * FROM {self.table} ORDER BY created_at DESC"
            ).fetchall()
        return [self._row_to_entity(row) for row in rows]

    def get(self, credential_id: int) -> BotCredential | None:
        with get_connection() as conn:
            row = conn.execute(
                f"SELECT * FROM {self.table} WHERE id = ?",
                (credential_id,),
            ).fetchone()
        return self._row_to_entity(row) if row else None

    def create(self, data: dict) -> BotCredential:
        token = (data.get("token") or "").strip()
        if not token:
            raise ValueError("Токен обязателен")
        encrypted = encrypt_token(token)
        payload = {
            "name": (data.get("name") or "").strip(),
            "platform": (data.get("platform") or "telegram").strip().lower(),
            "encrypted_token": encrypted,
            "metadata": _serialize_metadata(data.get("metadata")),
            "is_active": 1 if data.get("is_active", True) else 0,
        }
        with get_connection() as conn:
            cur = conn.execute(
                """
                INSERT INTO bot_credentials(name, platform, encrypted_token, metadata, is_active)
                VALUES (:name, :platform, :encrypted_token, :metadata, :is_active)
                """,
                payload,
            )
            credential_id = cur.lastrowid
            row = conn.execute(
                f"SELECT * FROM {self.table} WHERE id = ?",
                (credential_id,),
            ).fetchone()
        return self._row_to_entity(row)

    def update(self, credential_id: int, data: dict) -> BotCredential:
        fields: list[str] = []
        params: dict = {"id": credential_id}
        if "name" in data:
            fields.append("name = :name")
            params["name"] = (data.get("name") or "").strip()
        if "platform" in data:
            fields.append("platform = :platform")
            params["platform"] = (data.get("platform") or "telegram").strip().lower()
        if "token" in data and data.get("token"):
            fields.append("encrypted_token = :encrypted_token")
            params["encrypted_token"] = encrypt_token((data.get("token") or "").strip())
        if "metadata" in data:
            fields.append("metadata = :metadata")
            params["metadata"] = _serialize_metadata(data.get("metadata"))
        if "is_active" in data:
            fields.append("is_active = :is_active")
            params["is_active"] = 1 if data.get("is_active") else 0
        if not fields:
            raise ValueError("Нет данных для обновления")
        with get_connection() as conn:
            conn.execute(
                f"UPDATE {self.table} SET {', '.join(fields)}, updated_at = datetime('now') WHERE id = :id",
                params,
            )
            row = conn.execute(
                f"SELECT * FROM {self.table} WHERE id = ?",
                (credential_id,),
            ).fetchone()
        return self._row_to_entity(row)

    def delete(self, credential_id: int) -> None:
        with get_connection() as conn:
            conn.execute(
                f"DELETE FROM {self.table} WHERE id = ?",
                (credential_id,),
            )

    def reveal_token(self, credential_id: int) -> str:
        credential = self.get(credential_id)
        if not credential:
            raise ValueError("Credential not found")
        try:
            return decrypt_token(credential.encrypted_token)
        except SecretStorageError as exc:
            raise ValueError(str(exc))

    @staticmethod
    def _row_to_entity(row: sqlite3.Row | None) -> BotCredential:
        if row is None:
            return None
        metadata = _parse_json(row["metadata"])
        try:
            revealed = decrypt_token(row["encrypted_token"]) if row["encrypted_token"] else ""
        except SecretStorageError:
            revealed = row["encrypted_token"] or ""
        masked = mask_token(revealed)
        return BotCredential(
            id=row["id"],
            name=row["name"],
            platform=row["platform"],
            encrypted_token=row["encrypted_token"],
            masked_token=masked,
            metadata=metadata,
            is_active=bool(row["is_active"]),
            created_at=row["created_at"],
            updated_at=row["updated_at"],
        )


class ChannelRepository:
    table = "channels"

    def list(self) -> list[Channel]:
        with get_connection() as conn:
            rows = conn.execute(
                """
                SELECT c.*, bc.name AS credential_name
                FROM channels c
                LEFT JOIN bot_credentials bc ON bc.id = c.credential_id
                ORDER BY c.created_at DESC
                """
            ).fetchall()
        return [self._row_to_entity(row) for row in rows]

    def get(self, channel_id: int) -> Channel | None:
        with get_connection() as conn:
            row = conn.execute(
                """
                SELECT c.*, bc.name AS credential_name
                FROM channels c
                LEFT JOIN bot_credentials bc ON bc.id = c.credential_id
                WHERE c.id = ?
                """,
                (channel_id,),
            ).fetchone()
        return self._row_to_entity(row) if row else None

    def create(self, data: dict) -> Channel:
        payload = {
            "channel_name": (data.get("name") or "").strip(),
            "description": (data.get("description") or "").strip(),
            "platform": (data.get("platform") or "telegram").strip().lower(),
            "credential_id": data.get("credential_id"),
            "platform_config": _serialize_metadata(data.get("settings")),
            "delivery_settings": _serialize_metadata(data.get("settings")),
            "filters": _serialize_metadata(data.get("filters")),
            "is_active": 1 if data.get("is_active", True) else 0,
            "public_id": (data.get("public_id") or secrets.token_hex(8)).lower(),
            "questions_cfg": _serialize_metadata(data.get("questions_cfg")),
            "max_questions": int(data.get("max_questions") or 0),
            "question_template_id": data.get("question_template_id"),
            "rating_template_id": data.get("rating_template_id"),
            "auto_action_template_id": data.get("auto_action_template_id"),
        }
        if not payload["channel_name"]:
            raise ValueError("Название канала обязательно")
        if not payload["credential_id"]:
            raise ValueError("credential_id обязателен")
        with get_connection() as conn:
            token_placeholder = "vault:{credential_id}".format(
                credential_id=payload["credential_id"] or "pending"
            )
            cur = conn.execute(
                """
                INSERT INTO channels(
                    token,
                    bot_name,
                    bot_username,
                    channel_name,
                    questions_cfg,
                    max_questions,
                    is_active,
                    question_template_id,
                    rating_template_id,
                    auto_action_template_id,
                    public_id,
                    platform,
                    platform_config,
                    credential_id,
                    description,
                    filters,
                    delivery_settings,
                    updated_at
                )
                VALUES (
                    :token,
                    '',
                    '',
                    :channel_name,
                    :questions_cfg,
                    :max_questions,
                    :is_active,
                    :question_template_id,
                    :rating_template_id,
                    :auto_action_template_id,
                    :public_id,
                    :platform,
                    :platform_config,
                    :credential_id,
                    :description,
                    :filters,
                    :delivery_settings,
                    datetime('now')
                )
                """,
                {
                    **payload,
                    "token": token_placeholder,
                },
            )
            channel_id = cur.lastrowid
            row = conn.execute(
                "SELECT * FROM channels WHERE id = ?",
                (channel_id,),
            ).fetchone()
        return self._row_to_entity(row)

    def update(self, channel_id: int, data: dict) -> Channel:
        columns = self._columns()
        fields: list[str] = []
        params: dict = {"id": channel_id}
        if "name" in data:
            fields.append("channel_name = :channel_name")
            params["channel_name"] = (data.get("name") or "").strip()
        if "description" in data:
            fields.append("description = :description")
            params["description"] = (data.get("description") or "").strip()
        if "platform" in data:
            fields.append("platform = :platform")
            params["platform"] = (data.get("platform") or "telegram").strip().lower()
        if "credential_id" in data:
            fields.append("credential_id = :credential_id")
            params["credential_id"] = data.get("credential_id")
        if "settings" in data:
            payload = _serialize_metadata(data.get("settings"))
            fields.append("platform_config = :platform_config")
            params["platform_config"] = payload
            if "delivery_settings" in columns:
                fields.append("delivery_settings = :delivery_settings")
                params["delivery_settings"] = payload
        if "filters" in data:
            fields.append("filters = :filters")
            params["filters"] = _serialize_metadata(data.get("filters"))
        if "is_active" in data:
            fields.append("is_active = :is_active")
            params["is_active"] = 1 if data.get("is_active") else 0
        if "credential_id" in data:
            fields.append("credential_id = :credential_id")
            params["credential_id"] = data.get("credential_id")
            fields.append("token = :token")
            placeholder_id = data.get("credential_id") or "pending"
            params["token"] = f"vault:{placeholder_id}"
        if "questions_cfg" in data:
            fields.append("questions_cfg = :questions_cfg")
            value = data.get("questions_cfg")
            params["questions_cfg"] = (
                json.dumps(value, ensure_ascii=False)
                if not isinstance(value, str)
                else value
            )
        if "max_questions" in data:
            fields.append("max_questions = :max_questions")
            params["max_questions"] = int(data.get("max_questions") or 0)
        if "question_template_id" in data:
            fields.append("question_template_id = :question_template_id")
            params["question_template_id"] = data.get("question_template_id")
        if "rating_template_id" in data:
            fields.append("rating_template_id = :rating_template_id")
            params["rating_template_id"] = data.get("rating_template_id")
        if "auto_action_template_id" in data:
            fields.append("auto_action_template_id = :auto_action_template_id")
            params["auto_action_template_id"] = data.get("auto_action_template_id")
        if not fields:
            raise ValueError("Нет данных для обновления")
        with get_connection() as conn:
            conn.execute(
                f"UPDATE channels SET {', '.join(fields)}, updated_at = datetime('now') WHERE id = :id",
                params,
            )
            row = conn.execute(
                "SELECT * FROM channels WHERE id = ?",
                (channel_id,),
            ).fetchone()
        return self._row_to_entity(row)

    def delete(self, channel_id: int) -> None:
        with get_connection() as conn:
            conn.execute("DELETE FROM channels WHERE id = ?", (channel_id,))

    def to_dict(self, channel: Channel) -> dict:
        if channel is None:
            return {}
        payload = asdict(channel)
        payload.pop("encrypted_token", None)
        return payload

    @staticmethod
    def _columns() -> set[str]:
        with get_connection() as conn:
            return {r["name"] for r in conn.execute("PRAGMA table_info(channels)")}

    @staticmethod
    def _row_to_entity(row: sqlite3.Row | None) -> Channel:
        if row is None:
            return None
        keys = set(row.keys())
        name = row["channel_name"] if "channel_name" in keys else row.get("name")
        platform = row["platform"] if "platform" in keys else "telegram"
        filters = _parse_json(row["filters"]) if "filters" in keys else {}
        delivery_settings = None
        if "delivery_settings" in keys:
            delivery_settings = _parse_json(row["delivery_settings"])
        platform_config = _parse_json(row["platform_config"]) if "platform_config" in keys else {}
        settings = delivery_settings if delivery_settings else platform_config
        return Channel(
            id=row["id"],
            name=name or "",
            description=row["description"] if "description" in keys and row["description"] else "",
            platform=platform,
            credential_id=row["credential_id"] if "credential_id" in keys else None,
            public_id=row["public_id"] if "public_id" in keys else None,
            settings=settings,
            filters=filters,
            is_active=bool(row["is_active"]) if "is_active" in keys else True,
            created_at=row["created_at"] if "created_at" in keys else None,
            updated_at=row["updated_at"] if "updated_at" in keys else row.get("created_at"),
        )


class ChannelNotificationRepository:
    table = "channel_notifications"

    def create(self, data: dict) -> ChannelNotification:
        payload = {
            "channel_id": data.get("channel_id"),
            "recipient": (data.get("recipient") or "").strip(),
            "payload": _serialize_metadata(data.get("payload")),
            "status": (data.get("status") or "pending").strip().lower(),
            "scheduled_at": data.get("scheduled_at") or "datetime('now')",
        }
        if not payload["channel_id"]:
            raise ValueError("channel_id обязателен")
        with get_connection() as conn:
            cur = conn.execute(
                """
                INSERT INTO channel_notifications(channel_id, recipient, payload, status, scheduled_at)
                VALUES (:channel_id, :recipient, :payload, :status, COALESCE(:scheduled_at, datetime('now')))
                """,
                payload,
            )
            notification_id = cur.lastrowid
            row = conn.execute(
                "SELECT * FROM channel_notifications WHERE id = ?",
                (notification_id,),
            ).fetchone()
        return self._row_to_entity(row)

    def list(self, *, status: str | None = None, channel_id: int | None = None, limit: int = 100) -> list[ChannelNotification]:
        query = "SELECT * FROM channel_notifications"
        clauses: list[str] = []
        params: list = []
        if status:
            clauses.append("status = ?")
            params.append(status)
        if channel_id:
            clauses.append("channel_id = ?")
            params.append(channel_id)
        if clauses:
            query += " WHERE " + " AND ".join(clauses)
        query += " ORDER BY created_at DESC LIMIT ?"
        params.append(limit)
        with get_connection() as conn:
            rows = conn.execute(query, params).fetchall()
        return [self._row_to_entity(row) for row in rows]

    def dequeue_pending(self, limit: int = 10) -> list[ChannelNotification]:
        with get_connection() as conn:
            rows = conn.execute(
                """
                SELECT * FROM channel_notifications
                WHERE status IN ('pending', 'retry')
                ORDER BY scheduled_at ASC, created_at ASC
                LIMIT ?
                """,
                (limit,),
            ).fetchall()
        return [self._row_to_entity(row) for row in rows]

    def get(self, notification_id: int) -> ChannelNotification | None:
        with get_connection() as conn:
            row = conn.execute(
                "SELECT * FROM channel_notifications WHERE id = ?",
                (notification_id,),
            ).fetchone()
        return self._row_to_entity(row) if row else None

    def mark_in_progress(self, notification_id: int) -> None:
        with get_connection() as conn:
            conn.execute(
                """
                UPDATE channel_notifications
                SET status = 'in_progress', started_at = datetime('now'), attempts = attempts + 1
                WHERE id = ?
                """,
                (notification_id,),
            )

    def mark_completed(self, notification_id: int) -> None:
        with get_connection() as conn:
            conn.execute(
                """
                UPDATE channel_notifications
                SET status = 'done', finished_at = datetime('now'), error = NULL
                WHERE id = ?
                """,
                (notification_id,),
            )

    def mark_failed(self, notification_id: int, message: str) -> None:
        with get_connection() as conn:
            conn.execute(
                """
                UPDATE channel_notifications
                SET status = 'failed', finished_at = datetime('now'), error = ?
                WHERE id = ?
                """,
                (message, notification_id),
            )

    @staticmethod
    def _row_to_entity(row: sqlite3.Row | None) -> ChannelNotification:
        if row is None:
            return None
        return ChannelNotification(
            id=row["id"],
            channel_id=row["channel_id"],
            recipient=row["recipient"],
            payload=_parse_json(row["payload"]),
            status=row["status"],
            error=row["error"],
            attempts=row["attempts"],
            scheduled_at=row["scheduled_at"],
            created_at=row["created_at"],
            started_at=row["started_at"],
            finished_at=row["finished_at"],
        )


def ensure_tables() -> None:
    with get_connection() as conn:
        conn.execute(
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
            """
        )

        cols = {row["name"] for row in conn.execute("PRAGMA table_info(channels)")}
        if "credential_id" not in cols:
            conn.execute(
                "ALTER TABLE channels ADD COLUMN credential_id INTEGER REFERENCES bot_credentials(id)"
            )
        if "description" not in cols:
            conn.execute("ALTER TABLE channels ADD COLUMN description TEXT")
        if "filters" not in cols:
            conn.execute("ALTER TABLE channels ADD COLUMN filters TEXT DEFAULT '{}'")
        if "delivery_settings" not in cols:
            conn.execute("ALTER TABLE channels ADD COLUMN delivery_settings TEXT DEFAULT '{}' ")
        if "platform" not in cols:
            conn.execute("ALTER TABLE channels ADD COLUMN platform TEXT NOT NULL DEFAULT 'telegram'")
        if "platform_config" not in cols:
            conn.execute("ALTER TABLE channels ADD COLUMN platform_config TEXT DEFAULT '{}' ")
        if "auto_action_template_id" not in cols:
            conn.execute("ALTER TABLE channels ADD COLUMN auto_action_template_id TEXT")
        if "updated_at" not in cols:
            try:
                conn.execute(
                    "ALTER TABLE channels ADD COLUMN updated_at TEXT NOT NULL DEFAULT (datetime('now'))"
                )
            except sqlite3.OperationalError as exc:
                message = str(exc).lower()
                if "non-constant default" not in message:
                    raise
                conn.execute("ALTER TABLE channels ADD COLUMN updated_at TEXT")
                conn.execute(
                    """
                    UPDATE channels
                    SET updated_at = datetime('now')
                    WHERE updated_at IS NULL OR TRIM(COALESCE(updated_at, '')) = ''
                    """
                )
        if "support_chat_id" not in cols:
            try:
                conn.execute("ALTER TABLE channels ADD COLUMN support_chat_id TEXT")
            except sqlite3.OperationalError:
                pass

        # Миграция существующих токенов в секретное хранилище
        rows = conn.execute(
            """
            SELECT id, channel_name, token, platform
            FROM channels
            WHERE TRIM(COALESCE(token, '')) != '' AND token NOT LIKE 'vault:%'
            """
        ).fetchall()
        for row in rows:
            token = row["token"]
            metadata = json.dumps({"channel_id": row["id"]}, ensure_ascii=False)
            existing = conn.execute(
                "SELECT id FROM bot_credentials WHERE metadata = ?",
                (metadata,),
            ).fetchone()
            if existing:
                credential_id = existing["id"] if isinstance(existing, sqlite3.Row) else existing[0]
            else:
                try:
                    encrypted = encrypt_token(token)
                except SecretStorageError:
                    continue
                cur = conn.execute(
                    """
                    INSERT INTO bot_credentials(name, platform, encrypted_token, metadata)
                    VALUES (?, ?, ?, ?)
                    """,
                    (
                        row["channel_name"] or f"Channel {row['id']}",
                        (row["platform"] or "telegram") if "platform" in cols else "telegram",
                        encrypted,
                        metadata,
                    ),
                )
                credential_id = cur.lastrowid
            conn.execute(
                "UPDATE channels SET credential_id = ?, token = ?, updated_at = datetime('now') WHERE id = ?",
                (credential_id, f"vault:{credential_id}", row["id"]),
            )

        conn.execute(
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
            """
        )

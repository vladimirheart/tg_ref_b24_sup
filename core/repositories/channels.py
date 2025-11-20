"""Repositories encapsulating access to channel related tables via SQLAlchemy."""
from __future__ import annotations

import json
from datetime import datetime
from typing import Any

from sqlalchemy import func, select
from sqlalchemy.orm import joinedload

from migrations_runner import ensure_schema_is_current

from ..database import SessionLocal, session_scope
from ..models import BotCredential, Channel, ChannelNotification
from ..secrets import (
    SecretStorageError,
    decrypt_token,
    encrypt_token,
    mask_token,
)


def _serialize_metadata(value: Any) -> str:
    if isinstance(value, str):
        return value
    try:
        return json.dumps(value or {}, ensure_ascii=False)
    except TypeError:
        return "{}"


def _parse_json(value: Any) -> dict:
    if not value:
        return {}
    if isinstance(value, dict):
        return value
    try:
        parsed = json.loads(value)
    except (TypeError, json.JSONDecodeError):
        return {}
    return parsed if isinstance(parsed, dict) else {}


class BotCredentialRepository:
    """Persistence layer for bot credential records."""

    def __init__(self, session_factory: Any = None):
        self._session_factory = session_factory or SessionLocal

    def list(self) -> list[BotCredential]:
        with session_scope(self._session_factory) as session:
            stmt = select(BotCredential).order_by(BotCredential.created_at.desc())
            return list(session.execute(stmt).scalars())

    def get(self, credential_id: int) -> BotCredential | None:
        with session_scope(self._session_factory) as session:
            return session.get(BotCredential, credential_id)

    def create(self, data: dict) -> BotCredential:
        token = (data.get("token") or "").strip()
        if not token:
            raise ValueError("Токен обязателен")
        encrypted = encrypt_token(token)
        payload = BotCredential(
            name=(data.get("name") or "").strip(),
            platform=(data.get("platform") or "telegram").strip().lower(),
            encrypted_token=encrypted,
            metadata_payload=_serialize_metadata(data.get("metadata")),
            is_active=bool(data.get("is_active", True)),
        )
        with session_scope(self._session_factory) as session:
            session.add(payload)
            session.flush()
            session.refresh(payload)
            return payload

    def update(self, credential_id: int, data: dict) -> BotCredential:
        with session_scope(self._session_factory) as session:
            credential = session.get(BotCredential, credential_id)
            if credential is None:
                raise ValueError("Credential not found")
            if "name" in data:
                credential.name = (data.get("name") or "").strip()
            if "platform" in data:
                credential.platform = (data.get("platform") or "telegram").strip().lower()
            if data.get("token"):
                credential.encrypted_token = encrypt_token((data.get("token") or "").strip())
            if "metadata" in data:
                credential.metadata_payload = _serialize_metadata(data.get("metadata"))
            if "is_active" in data:
                credential.is_active = bool(data.get("is_active"))
            session.flush()
            session.refresh(credential)
            return credential

    def delete(self, credential_id: int) -> None:
        with session_scope(self._session_factory) as session:
            credential = session.get(BotCredential, credential_id)
            if credential:
                session.delete(credential)

    def reveal_token(self, credential_id: int) -> str:
        credential = self.get(credential_id)
        if not credential:
            raise ValueError("Credential not found")
        try:
            return decrypt_token(credential.encrypted_token)
        except SecretStorageError as exc:
            raise ValueError(str(exc))

    @staticmethod
    def to_dict(credential: BotCredential | None) -> dict[str, Any]:
        if credential is None:
            return {}
        try:
            revealed = decrypt_token(credential.encrypted_token) if credential.encrypted_token else ""
        except SecretStorageError:
            revealed = credential.encrypted_token or ""
        masked = mask_token(revealed)
        return {
            "id": credential.id,
            "name": credential.name,
            "platform": credential.platform,
            "encrypted_token": credential.encrypted_token,
            "masked_token": masked,
            "metadata": _parse_json(credential.metadata_payload),
            "is_active": bool(credential.is_active),
            "created_at": credential.created_at,
            "updated_at": credential.updated_at,
        }


class ChannelRepository:
    """Persistence layer for channels with helper queries for bots."""

    def __init__(self, session_factory: Any = None):
        self._session_factory = session_factory or SessionLocal

    def list(self) -> list[Channel]:
        with session_scope(self._session_factory) as session:
            stmt = select(Channel).order_by(Channel.created_at.desc())
            return list(session.execute(stmt).scalars())

    def get(self, channel_id: int) -> Channel | None:
        with session_scope(self._session_factory) as session:
            return session.get(Channel, channel_id)

    def list_active(self, platform: str | None = None) -> list[Channel]:
        with session_scope(self._session_factory) as session:
            stmt = select(Channel).options(joinedload(Channel.credential)).where(Channel.is_active == True)  # noqa: E712
            if platform:
                stmt = stmt.where(func.lower(Channel.platform) == platform.lower())
            return list(session.execute(stmt).scalars())

    def list_with_tokens(self) -> list[tuple[int, str | None]]:
        with session_scope(self._session_factory) as session:
            stmt = (
                select(Channel.id, BotCredential.encrypted_token)
                .join(BotCredential, Channel.credential_id == BotCredential.id, isouter=True)
            )
            return list(session.execute(stmt).all())

    def create(self, data: dict) -> Channel:
        payload = Channel(
            channel_name=(data.get("name") or "").strip(),
            description=(data.get("description") or "").strip(),
            platform=(data.get("platform") or "telegram").strip().lower(),
            credential_id=data.get("credential_id"),
            platform_config=_serialize_metadata(data.get("settings")),
            delivery_settings=_serialize_metadata(data.get("delivery_settings") or data.get("settings")),
            filters=_serialize_metadata(data.get("filters")),
            is_active=bool(data.get("is_active", True)),
            public_id=(data.get("public_id") or "").strip().lower() or None,
            questions_cfg=_serialize_metadata(data.get("questions_cfg")),
            max_questions=int(data.get("max_questions") or 0),
            question_template_id=data.get("question_template_id"),
            rating_template_id=data.get("rating_template_id"),
            auto_action_template_id=data.get("auto_action_template_id"),
            support_chat_id=(
                str(data.get("support_chat_id")).strip() if data.get("support_chat_id") not in (None, "") else None
            ),
            token=f"vault:{data.get('credential_id') or 'pending'}",
        )
        if not payload.channel_name:
            raise ValueError("Название канала обязательно")
        if not payload.credential_id:
            raise ValueError("credential_id обязателен")
        with session_scope(self._session_factory) as session:
            session.add(payload)
            session.flush()
            session.refresh(payload)
            return payload

    def update(self, channel_id: int, data: dict) -> Channel:
        with session_scope(self._session_factory) as session:
            channel = session.get(Channel, channel_id)
            if channel is None:
                raise ValueError("Channel not found")
            if "name" in data:
                channel.channel_name = (data.get("name") or "").strip()
            if "description" in data:
                channel.description = (data.get("description") or "").strip()
            if "platform" in data:
                channel.platform = (data.get("platform") or "telegram").strip().lower()
            if "credential_id" in data:
                channel.credential_id = data.get("credential_id")
                placeholder_id = data.get("credential_id") or "pending"
                channel.token = f"vault:{placeholder_id}"
            if "settings" in data:
                payload = _serialize_metadata(data.get("settings"))
                channel.platform_config = payload
                channel.delivery_settings = payload
            if "filters" in data:
                channel.filters = _serialize_metadata(data.get("filters"))
            if "is_active" in data:
                channel.is_active = bool(data.get("is_active"))
            if "questions_cfg" in data:
                channel.questions_cfg = _serialize_metadata(data.get("questions_cfg"))
            if "max_questions" in data:
                channel.max_questions = int(data.get("max_questions") or 0)
            if "question_template_id" in data:
                channel.question_template_id = data.get("question_template_id")
            if "rating_template_id" in data:
                channel.rating_template_id = data.get("rating_template_id")
            if "auto_action_template_id" in data:
                channel.auto_action_template_id = data.get("auto_action_template_id")
            if "support_chat_id" in data:
                value = data.get("support_chat_id")
                channel.support_chat_id = str(value).strip() if value not in (None, "") else None
            if "public_id" in data:
                channel.public_id = (data.get("public_id") or "").strip().lower() or None
            session.flush()
            session.refresh(channel)
            return channel

    def delete(self, channel_id: int) -> None:
        with session_scope(self._session_factory) as session:
            channel = session.get(Channel, channel_id)
            if channel:
                session.delete(channel)

    def public_id_exists(self, public_id: str) -> bool:
        if not public_id:
            return False
        with session_scope(self._session_factory) as session:
            stmt = select(func.count()).where(func.lower(Channel.public_id) == public_id.lower())
            return session.execute(stmt).scalar_one() > 0

    @staticmethod
    def to_dict(channel: Channel | None) -> dict[str, Any]:
        if channel is None:
            return {}
        settings = _parse_json(channel.delivery_settings) or _parse_json(channel.platform_config)
        return {
            "id": channel.id,
            "name": channel.channel_name,
            "description": channel.description,
            "platform": channel.platform,
            "credential_id": channel.credential_id,
            "public_id": channel.public_id,
            "settings": settings,
            "filters": _parse_json(channel.filters),
            "is_active": bool(channel.is_active),
            "support_chat_id": channel.support_chat_id,
            "created_at": channel.created_at,
            "updated_at": channel.updated_at,
        }


class ChannelNotificationRepository:
    """Persistence layer for channel notification queue."""

    def __init__(self, session_factory: Any = None):
        self._session_factory = session_factory or SessionLocal

    def create(self, data: dict) -> ChannelNotification:
        payload = ChannelNotification(
            channel_id=data.get("channel_id"),
            recipient=(data.get("recipient") or "").strip(),
            payload=_serialize_metadata(data.get("payload")),
            status=(data.get("status") or "pending").strip().lower(),
            scheduled_at=data.get("scheduled_at"),
        )
        if not payload.channel_id:
            raise ValueError("channel_id обязателен")
        with session_scope(self._session_factory) as session:
            session.add(payload)
            session.flush()
            session.refresh(payload)
            return payload

    def list(
        self,
        *,
        status: str | None = None,
        channel_id: int | None = None,
        limit: int = 100,
    ) -> list[ChannelNotification]:
        with session_scope(self._session_factory) as session:
            stmt = select(ChannelNotification)
            if status:
                stmt = stmt.where(ChannelNotification.status == status)
            if channel_id:
                stmt = stmt.where(ChannelNotification.channel_id == channel_id)
            stmt = stmt.order_by(ChannelNotification.created_at.desc()).limit(limit)
            return list(session.execute(stmt).scalars())

    def dequeue_pending(self, limit: int = 10) -> list[ChannelNotification]:
        with session_scope(self._session_factory) as session:
            stmt = (
                select(ChannelNotification)
                .where(ChannelNotification.status.in_(["pending", "retry"]))
                .order_by(ChannelNotification.scheduled_at.asc(), ChannelNotification.created_at.asc())
                .limit(limit)
            )
            return list(session.execute(stmt).scalars())

    def get(self, notification_id: int) -> ChannelNotification | None:
        with session_scope(self._session_factory) as session:
            return session.get(ChannelNotification, notification_id)

    def mark_in_progress(self, notification_id: int) -> None:
        self._update_status(notification_id, "in_progress", started=True)

    def mark_completed(self, notification_id: int) -> None:
        self._update_status(notification_id, "done", finished=True, clear_error=True)

    def mark_failed(self, notification_id: int, message: str) -> None:
        self._update_status(notification_id, "failed", finished=True, error_message=message)

    def _update_status(
        self,
        notification_id: int,
        status: str,
        *,
        started: bool = False,
        finished: bool = False,
        clear_error: bool = False,
        error_message: str | None = None,
    ) -> None:
        with session_scope(self._session_factory) as session:
            notification = session.get(ChannelNotification, notification_id)
            if notification is None:
                return
            notification.status = status
            if started:
                notification.started_at = datetime.utcnow().isoformat(timespec="seconds")
                notification.attempts = (notification.attempts or 0) + 1
            if finished:
                notification.finished_at = datetime.utcnow().isoformat(timespec="seconds")
            if clear_error:
                notification.error = None
            if error_message:
                notification.error = error_message


def ensure_tables() -> None:
    """Ensure the database schema is up to date."""

    ensure_schema_is_current()

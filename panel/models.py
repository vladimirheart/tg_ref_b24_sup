"""Dataclasses describing channel management entities."""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime
from typing import Any


def _utcnow() -> str:
    return datetime.utcnow().isoformat(timespec="seconds")


@dataclass(slots=True)
class BotCredential:
    id: int | None = None
    name: str = ""
    platform: str = "telegram"
    encrypted_token: str = ""
    masked_token: str = ""
    metadata: dict[str, Any] = field(default_factory=dict)
    is_active: bool = True
    created_at: str = field(default_factory=_utcnow)
    updated_at: str = field(default_factory=_utcnow)


@dataclass(slots=True)
class Channel:
    id: int | None = None
    name: str = ""
    description: str = ""
    platform: str = "telegram"
    credential_id: int | None = None
    public_id: str | None = None
    settings: dict[str, Any] = field(default_factory=dict)
    is_active: bool = True
    filters: dict[str, Any] = field(default_factory=dict)
    created_at: str = field(default_factory=_utcnow)
    updated_at: str = field(default_factory=_utcnow)


@dataclass(slots=True)
class ChannelNotification:
    id: int | None = None
    channel_id: int | None = None
    recipient: str = ""
    payload: dict[str, Any] = field(default_factory=dict)
    status: str = "pending"
    error: str | None = None
    attempts: int = 0
    scheduled_at: str = field(default_factory=_utcnow)
    created_at: str = field(default_factory=_utcnow)
    started_at: str | None = None
    finished_at: str | None = None

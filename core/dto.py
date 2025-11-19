"""Data transfer objects describing channel/ticket payloads."""
from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any


@dataclass(slots=True)
class ChannelDTO:
    id: int
    name: str
    description: str | None
    platform: str
    credential_id: int | None
    public_id: str | None
    settings: dict[str, Any] = field(default_factory=dict)
    filters: dict[str, Any] = field(default_factory=dict)
    is_active: bool = True
    support_chat_id: str | None = None
    question_template_id: str | None = None
    rating_template_id: str | None = None
    questions_cfg: dict[str, Any] = field(default_factory=dict)
    max_questions: int = 0
    auto_action_template_id: str | None = None


@dataclass(slots=True)
class ChannelBotConfig:
    id: int
    token: str
    platform: str
    platform_config: dict[str, Any] = field(default_factory=dict)


@dataclass(slots=True)
class ChannelQuestionnaire:
    channel_id: int
    question_template_id: str | None
    rating_template_id: str | None
    questions_cfg: dict[str, Any] = field(default_factory=dict)
    max_questions: int = 0


@dataclass(slots=True)
class TicketSummary:
    ticket_id: str
    user_id: int
    status: str | None
    resolved_at: str | None
    resolved_by: str | None
    channel_id: int | None
    reopen_count: int | None
    closed_count: int | None


@dataclass(slots=True)
class TicketStatusStats:
    counts: dict[str, int] = field(default_factory=dict)

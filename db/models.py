"""SQLAlchemy models covering gradually migrated tables."""
from __future__ import annotations

from typing import Optional

from sqlalchemy import Index, Integer, String, Text
from sqlalchemy.orm import Mapped, mapped_column

from .session import Base


class ClientBlacklist(Base):
    """Represents a record of a blacklisted Telegram user."""

    __tablename__ = "client_blacklist"

    user_id: Mapped[str] = mapped_column(String, primary_key=True)
    is_blacklisted: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    reason: Mapped[Optional[str]] = mapped_column(Text)
    added_at: Mapped[Optional[str]] = mapped_column(String)
    added_by: Mapped[Optional[str]] = mapped_column(String)
    unblock_requested: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    unblock_requested_at: Mapped[Optional[str]] = mapped_column(String)


class ClientUnblockRequest(Base):
    """Represents a user's request to lift a blacklist restriction."""

    __tablename__ = "client_unblock_requests"
    __table_args__ = (Index("idx_client_unblock_requests_user", "user_id"),)

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    user_id: Mapped[str] = mapped_column(String, nullable=False, index=True)
    channel_id: Mapped[Optional[int]] = mapped_column(Integer)
    reason: Mapped[Optional[str]] = mapped_column(Text)
    created_at: Mapped[str] = mapped_column(String, nullable=False)
    status: Mapped[str] = mapped_column(String, nullable=False, default="pending")
    decided_at: Mapped[Optional[str]] = mapped_column(String)
    decided_by: Mapped[Optional[str]] = mapped_column(String)
    decision_comment: Mapped[Optional[str]] = mapped_column(Text)


class Task(Base):
    """Subset of the CRM task table used for automated records."""

    __tablename__ = "tasks"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    seq: Mapped[int] = mapped_column(Integer, nullable=False)
    source: Mapped[Optional[str]] = mapped_column(String)
    title: Mapped[Optional[str]] = mapped_column(Text)
    body_html: Mapped[Optional[str]] = mapped_column(Text)
    creator: Mapped[Optional[str]] = mapped_column(String)
    assignee: Mapped[Optional[str]] = mapped_column(String)
    tag: Mapped[Optional[str]] = mapped_column(String)
    status: Mapped[Optional[str]] = mapped_column(String, default="Новая")
    due_at: Mapped[Optional[str]] = mapped_column(String)
    created_at: Mapped[Optional[str]] = mapped_column(String)
    closed_at: Mapped[Optional[str]] = mapped_column(String)
    last_activity_at: Mapped[Optional[str]] = mapped_column(String)

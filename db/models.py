"""Minimal SQLAlchemy models required by legacy bot code."""
from __future__ import annotations

from sqlalchemy import Integer, String, Text
from sqlalchemy.orm import Mapped, mapped_column

from core.database import Base


class ClientBlacklist(Base):
    """ORM mapping for the ``client_blacklist`` table."""

    __tablename__ = "client_blacklist"

    user_id: Mapped[str] = mapped_column(String, primary_key=True)
    is_blacklisted: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    reason: Mapped[str | None] = mapped_column(Text)
    added_at: Mapped[str | None] = mapped_column(String)
    added_by: Mapped[str | None] = mapped_column(String)
    unblock_requested: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    unblock_requested_at: Mapped[str | None] = mapped_column(String)


class ClientUnblockRequest(Base):
    """ORM mapping for the ``client_unblock_requests`` table."""

    __tablename__ = "client_unblock_requests"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    user_id: Mapped[str] = mapped_column(String, nullable=False)
    channel_id: Mapped[int | None] = mapped_column(Integer)
    reason: Mapped[str | None] = mapped_column(Text)
    created_at: Mapped[str] = mapped_column(String, nullable=False)
    status: Mapped[str] = mapped_column(String, nullable=False, default="pending")
    decided_at: Mapped[str | None] = mapped_column(String)
    decided_by: Mapped[str | None] = mapped_column(String)
    decision_comment: Mapped[str | None] = mapped_column(Text)


class Task(Base):
    """ORM mapping for the ``tasks`` table."""

    __tablename__ = "tasks"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    seq: Mapped[int] = mapped_column(Integer, nullable=False)
    source: Mapped[str | None] = mapped_column(String)
    title: Mapped[str | None] = mapped_column(String)
    body_html: Mapped[str | None] = mapped_column(Text)
    creator: Mapped[str | None] = mapped_column(String)
    assignee: Mapped[str | None] = mapped_column(String)
    tag: Mapped[str | None] = mapped_column(String)
    status: Mapped[str | None] = mapped_column(String, default="Новая")
    due_at: Mapped[str | None] = mapped_column(String)
    created_at: Mapped[str | None] = mapped_column(String)
    closed_at: Mapped[str | None] = mapped_column(String)
    last_activity_at: Mapped[str | None] = mapped_column(String)


__all__ = ["ClientBlacklist", "ClientUnblockRequest", "Task"]

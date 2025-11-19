"""ORM models shared between panel and bot runtimes."""
from __future__ import annotations

from sqlalchemy import Boolean, Column, ForeignKey, Integer, String, Text
from sqlalchemy.orm import Mapped, mapped_column, relationship

from .database import Base


class BotCredential(Base):
    __tablename__ = "bot_credentials"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    name: Mapped[str] = mapped_column(String, nullable=False)
    platform: Mapped[str] = mapped_column(String, nullable=False, default="telegram")
    encrypted_token: Mapped[str] = mapped_column(Text, nullable=False)
    metadata_payload: Mapped[str | None] = mapped_column("metadata", Text, nullable=True)
    is_active: Mapped[bool] = mapped_column(Boolean, default=True)
    created_at: Mapped[str | None] = mapped_column(String)
    updated_at: Mapped[str | None] = mapped_column(String)

    channels: Mapped[list["Channel"]] = relationship(back_populates="credential")


class Channel(Base):
    __tablename__ = "channels"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    token: Mapped[str | None] = mapped_column(String)
    bot_name: Mapped[str | None] = mapped_column(String)
    bot_username: Mapped[str | None] = mapped_column(String)
    channel_name: Mapped[str] = mapped_column(String, nullable=False)
    questions_cfg: Mapped[str | None] = mapped_column(Text)
    max_questions: Mapped[int | None] = mapped_column(Integer, default=0)
    is_active: Mapped[bool] = mapped_column(Boolean, default=True)
    question_template_id: Mapped[str | None] = mapped_column(String)
    rating_template_id: Mapped[str | None] = mapped_column(String)
    auto_action_template_id: Mapped[str | None] = mapped_column(String)
    public_id: Mapped[str | None] = mapped_column(String)
    description: Mapped[str | None] = mapped_column(Text)
    filters: Mapped[str | None] = mapped_column(Text)
    delivery_settings: Mapped[str | None] = mapped_column(Text)
    platform: Mapped[str] = mapped_column(String, default="telegram")
    platform_config: Mapped[str | None] = mapped_column(Text)
    credential_id: Mapped[int | None] = mapped_column(ForeignKey("bot_credentials.id"))
    created_at: Mapped[str | None] = mapped_column(String)
    updated_at: Mapped[str | None] = mapped_column(String)
    support_chat_id: Mapped[str | None] = mapped_column(String)

    credential: Mapped[BotCredential | None] = relationship(back_populates="channels")


class ChannelNotification(Base):
    __tablename__ = "channel_notifications"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    channel_id: Mapped[int] = mapped_column(ForeignKey("channels.id"), nullable=False)
    recipient: Mapped[str | None] = mapped_column(String)
    payload: Mapped[str | None] = mapped_column(Text)
    status: Mapped[str] = mapped_column(String, default="pending")
    error: Mapped[str | None] = mapped_column(Text)
    attempts: Mapped[int] = mapped_column(Integer, default=0)
    scheduled_at: Mapped[str | None] = mapped_column(String)
    created_at: Mapped[str | None] = mapped_column(String)
    started_at: Mapped[str | None] = mapped_column(String)
    finished_at: Mapped[str | None] = mapped_column(String)


class Ticket(Base):
    __tablename__ = "tickets"
    __table_args__ = {"sqlite_with_rowid": False}

    user_id: Mapped[int] = mapped_column(Integer, primary_key=True)
    ticket_id: Mapped[str] = mapped_column(String, primary_key=True)
    group_msg_id: Mapped[int | None] = mapped_column(Integer)
    status: Mapped[str | None] = mapped_column(String)
    resolved_at: Mapped[str | None] = mapped_column(String)
    resolved_by: Mapped[str | None] = mapped_column(String)
    channel_id: Mapped[int | None] = mapped_column(Integer, ForeignKey("channels.id"))
    reopen_count: Mapped[int | None] = mapped_column(Integer)
    closed_count: Mapped[int | None] = mapped_column(Integer)
    work_time_total_sec: Mapped[int | None] = mapped_column(Integer)
    last_reopen_at: Mapped[str | None] = mapped_column(String)

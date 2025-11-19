"""Shared core package with ORM models and services for bots and panel."""

from .database import Base, SessionLocal, session_scope
from .channels import ChannelService
from .tickets import TicketService
from .dto import (
    ChannelBotConfig,
    ChannelDTO,
    ChannelQuestionnaire,
)
from .models import BotCredential, Channel, ChannelNotification, Ticket

__all__ = [
    "Base",
    "SessionLocal",
    "ChannelService",
    "TicketService",
    "ChannelDTO",
    "ChannelQuestionnaire",
    "ChannelBotConfig",
    "BotCredential",
    "Channel",
    "ChannelNotification",
    "Ticket",
    "session_scope",
]

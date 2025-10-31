"""Public interface for the SQLAlchemy database helpers."""
from __future__ import annotations

from .models import ClientBlacklist, ClientUnblockRequest, Task
from .session import Base, get_engine, get_session_factory, session_scope
from .setup import (
    ensure_client_blacklist_schema,
    ensure_client_unblock_requests_schema,
    ensure_core_tables,
)

__all__ = [
    "Base",
    "ClientBlacklist",
    "ClientUnblockRequest",
    "Task",
    "ensure_client_blacklist_schema",
    "ensure_client_unblock_requests_schema",
    "ensure_core_tables",
    "get_engine",
    "get_session_factory",
    "session_scope",
]

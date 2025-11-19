"""Expose session helpers for backwards compatibility."""
from __future__ import annotations

from core.database import Base, SessionLocal, session_scope

__all__ = ["Base", "SessionLocal", "session_scope"]

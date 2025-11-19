"""Ticket repository implemented with SQLAlchemy."""
from __future__ import annotations

from pathlib import Path
from typing import Any

from sqlalchemy import create_engine, func, select, text
from sqlalchemy.orm import sessionmaker

from ..database import SessionLocal, session_scope
from ..models import Ticket


class TicketRepository:
    """Simple read-only repository for ticket aggregates."""

    def __init__(self, session_factory: Any = None):
        if isinstance(session_factory, (str, Path)):
            engine = create_engine(f"sqlite:///{session_factory}", future=True)
            self._session_factory = sessionmaker(bind=engine, autoflush=False, autocommit=False)
        else:
            self._session_factory = session_factory or SessionLocal

    def list_recent(self, limit: int = 20) -> list[Ticket]:
        limit = max(1, int(limit))
        with session_scope(self._session_factory) as session:
            stmt = text(
                """
                SELECT ticket_id, user_id, status, resolved_at, resolved_by, channel_id,
                       reopen_count, closed_count
                FROM tickets
                ORDER BY COALESCE(resolved_at, '') DESC, ticket_id DESC
                LIMIT :limit
                """
            )
            rows = session.execute(stmt, {"limit": limit}).mappings().all()
        return [dict(row) for row in rows]

    def count_by_status(self) -> dict[str, int]:
        with session_scope(self._session_factory) as session:
            stmt = select(Ticket.status, func.count()).group_by(Ticket.status)
            rows = session.execute(stmt).all()
        summary: dict[str, int] = {}
        for status, count in rows:
            key = status or "unknown"
            summary[key] = summary.get(key, 0) + int(count)
        return summary

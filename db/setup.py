"""Helpers for bootstrapping SQLAlchemy-managed tables."""
from __future__ import annotations

from .models import ClientBlacklist, ClientUnblockRequest
from .session import Base, get_engine


def ensure_client_blacklist_schema() -> None:
    """Ensure the client blacklist table exists."""
    engine = get_engine()
    ClientBlacklist.__table__.create(bind=engine, checkfirst=True)


def ensure_client_unblock_requests_schema() -> None:
    """Ensure the client unblock requests table exists."""
    engine = get_engine()
    ClientUnblockRequest.__table__.create(bind=engine, checkfirst=True)


def ensure_core_tables() -> None:
    """Create all SQLAlchemy-managed tables if they are missing."""
    engine = get_engine()
    Base.metadata.create_all(
        engine,
        tables=[ClientBlacklist.__table__, ClientUnblockRequest.__table__],
        checkfirst=True,
    )

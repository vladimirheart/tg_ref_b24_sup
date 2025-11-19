"""Utility helpers that ensure legacy tables exist."""
from __future__ import annotations

from migrations_runner import ensure_schema_is_current


def ensure_client_blacklist_schema() -> None:
    """Ensure the ``client_blacklist`` table exists.

    All of our tables are managed through Alembic migrations, so keeping the
    schema up to date is equivalent to applying the latest migration.
    """

    ensure_schema_is_current()


def ensure_client_unblock_requests_schema() -> None:
    """Ensure the ``client_unblock_requests`` table exists."""

    ensure_schema_is_current()


__all__ = ["ensure_client_blacklist_schema", "ensure_client_unblock_requests_schema"]

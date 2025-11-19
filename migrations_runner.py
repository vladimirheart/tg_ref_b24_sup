"""Helpers for running Alembic migrations programmatically."""
from __future__ import annotations

from pathlib import Path
from typing import Final

from alembic import command
from alembic.config import Config

from config import DATABASE_URL

_APPLIED: bool = False
_CONFIG_PATH: Final[Path] = Path(__file__).resolve().with_name("alembic.ini")
_SCRIPT_LOCATION: Final[Path] = Path(__file__).resolve().parent / "migrations"


def upgrade_database(target: str = "head") -> None:
    """Apply Alembic migrations up to the requested target revision."""

    global _APPLIED
    if _APPLIED:
        return

    alembic_cfg = Config(str(_CONFIG_PATH))
    alembic_cfg.set_main_option("script_location", str(_SCRIPT_LOCATION))
    alembic_cfg.set_main_option("sqlalchemy.url", DATABASE_URL)
    command.upgrade(alembic_cfg, target)
    _APPLIED = True


def ensure_schema_is_current() -> None:
    """Public wrapper that keeps the schema on the latest revision."""

    upgrade_database("head")

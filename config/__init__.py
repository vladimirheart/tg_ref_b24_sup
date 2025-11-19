"""Runtime configuration helpers used across bots and the panel."""
from __future__ import annotations

from functools import lru_cache
from typing import Any

from .settings import Settings


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    """Return a cached Settings instance built from .env and shared JSON files."""

    return Settings.load()


def reload_settings() -> Settings:
    """Reset the cached settings and load them again."""

    get_settings.cache_clear()  # type: ignore[attr-defined]
    return get_settings()


def load_shared_settings() -> dict[str, Any]:
    """Load the combined shared settings JSON with basic validation."""

    return get_settings().shared.load_settings()


__all__ = ["Settings", "get_settings", "reload_settings", "load_shared_settings"]

"""Utilities for accessing shared JSON configuration files across Python and Java stacks."""
from __future__ import annotations

import json
from functools import lru_cache
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent
SHARED_CONFIG_DIR = BASE_DIR / "config" / "shared"


def shared_config_path(name: str) -> Path:
    """Return the absolute path to a shared configuration file."""
    return SHARED_CONFIG_DIR / name


@lru_cache(maxsize=None)
def load_shared_json(name: str):
    """Load a shared JSON file with caching to avoid repeated disk reads."""
    path = shared_config_path(name)
    with path.open("r", encoding="utf-8") as handle:
        return json.load(handle)

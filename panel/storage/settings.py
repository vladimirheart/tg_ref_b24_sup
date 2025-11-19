"""JSON-backed storage for runtime panel settings."""
from __future__ import annotations

import json
from pathlib import Path
from typing import Any


class SettingsStorage:
    def __init__(self, path: str | Path):
        self.path = Path(path)
        self.path.parent.mkdir(parents=True, exist_ok=True)
        if not self.path.exists():
            self._write({})

    def read(self) -> dict[str, Any]:
        try:
            with self.path.open("r", encoding="utf-8") as handle:
                return json.load(handle)
        except (FileNotFoundError, json.JSONDecodeError):
            return {}

    def write(self, payload: dict[str, Any]) -> None:
        self._write(payload)

    def patch(self, updates: dict[str, Any]) -> dict[str, Any]:
        data = self.read()
        data.update(updates)
        self._write(data)
        return data

    def _write(self, payload: dict[str, Any]) -> None:
        with self.path.open("w", encoding="utf-8") as handle:
            json.dump(payload, handle, ensure_ascii=False, indent=2, sort_keys=True)

"""Operations for runtime settings that are exposed to the UI."""
from __future__ import annotations

from typing import Any


class SettingsService:
    def __init__(self, storage):
        self.storage = storage

    def load(self) -> dict[str, Any]:
        return self.storage.read()

    def update(self, payload: dict[str, Any]) -> dict[str, Any]:
        if not isinstance(payload, dict):
            raise ValueError("Ожидался объект настроек")
        return self.storage.patch(payload)

    def get(self, key: str, default: Any = None) -> Any:
        return self.load().get(key, default)

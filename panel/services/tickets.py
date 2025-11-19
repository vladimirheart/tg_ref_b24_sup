"""Business operations around tickets."""
from __future__ import annotations

from datetime import datetime, timedelta
from typing import Any


class TicketService:
    def __init__(self, repository):
        self.repository = repository
        self._recent_cache: list[dict[str, Any]] = []
        self._cache_expires_at: datetime | None = None

    def list_recent(self, limit: int = 20, use_cache: bool = True) -> list[dict[str, Any]]:
        now = datetime.utcnow()
        if (
            use_cache
            and self._cache_expires_at is not None
            and self._cache_expires_at > now
            and self._recent_cache
        ):
            return self._recent_cache[:limit]
        tickets = self.repository.list_recent(limit)
        self._recent_cache = tickets
        self._cache_expires_at = now + timedelta(seconds=60)
        return tickets

    def status_summary(self) -> dict[str, int]:
        return self.repository.count_by_status()

    def refresh_cache(self) -> None:
        self.list_recent(use_cache=False)

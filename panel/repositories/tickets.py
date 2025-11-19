"""Ticket repository providing an aggregate view of the tickets table."""
from __future__ import annotations

import sqlite3
from collections import defaultdict
from pathlib import Path
from typing import Any


class TicketRepository:
    def __init__(self, db_path: str | Path):
        self.db_path = str(db_path)

    def _connect(self) -> sqlite3.Connection:
        conn = sqlite3.connect(self.db_path)
        conn.row_factory = sqlite3.Row
        return conn

    def list_recent(self, limit: int = 20) -> list[dict[str, Any]]:
        limit = max(1, int(limit))
        query = """
            SELECT ticket_id, user_id, status, resolved_at, resolved_by, channel_id,
                   reopen_count, closed_count
            FROM tickets
            ORDER BY IFNULL(resolved_at, '') DESC, ticket_id DESC
            LIMIT ?
        """
        with self._connect() as conn:
            rows = conn.execute(query, (limit,)).fetchall()
        return [dict(row) for row in rows]

    def count_by_status(self) -> dict[str, int]:
        query = "SELECT status, COUNT(1) AS count FROM tickets GROUP BY status"
        with self._connect() as conn:
            rows = conn.execute(query).fetchall()
        result: dict[str, int] = defaultdict(int)
        for row in rows:
            result[row["status"] or "unknown"] += row["count"]
        return dict(result)

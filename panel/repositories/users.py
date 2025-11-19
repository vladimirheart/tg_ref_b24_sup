"""User repository encapsulating access to the users database."""
from __future__ import annotations

import sqlite3
from dataclasses import asdict
from pathlib import Path
from typing import Any, Mapping


class UserRepository:
    """Wrapper around the SQLite database used for panel operators."""

    def __init__(self, db_path: str | Path):
        self.db_path = str(db_path)

    def _connect(self) -> sqlite3.Connection:
        conn = sqlite3.connect(self.db_path)
        conn.row_factory = sqlite3.Row
        return conn

    def find_by_username(self, username: str) -> dict[str, Any] | None:
        if not username:
            return None
        query = """
            SELECT u.*, r.name AS role_name, r.permissions AS role_permissions
            FROM users u
            LEFT JOIN roles r ON r.id = u.role_id
            WHERE LOWER(u.username) = LOWER(?)
        """
        with self._connect() as conn:
            row = conn.execute(query, (username,)).fetchone()
        return self._row_to_dict(row)

    def get_by_id(self, user_id: int) -> dict[str, Any] | None:
        if user_id is None:
            return None
        query = """
            SELECT u.*, r.name AS role_name, r.permissions AS role_permissions
            FROM users u
            LEFT JOIN roles r ON r.id = u.role_id
            WHERE u.id = ?
        """
        with self._connect() as conn:
            row = conn.execute(query, (user_id,)).fetchone()
        return self._row_to_dict(row)

    def list_active(self) -> list[dict[str, Any]]:
        query = """
            SELECT u.*, r.name AS role_name, r.permissions AS role_permissions
            FROM users u
            LEFT JOIN roles r ON r.id = u.role_id
            WHERE IFNULL(u.is_blocked, 0) = 0
            ORDER BY u.username
        """
        with self._connect() as conn:
            rows = conn.execute(query).fetchall()
        return [self._row_to_dict(row) for row in rows]

    @staticmethod
    def _row_to_dict(row: sqlite3.Row | Mapping[str, Any] | None) -> dict[str, Any] | None:
        if row is None:
            return None
        if isinstance(row, sqlite3.Row):
            return {key: row[key] for key in row.keys()}
        if isinstance(row, Mapping):
            return dict(row)
        return asdict(row) if hasattr(row, "__dataclass_fields__") else None

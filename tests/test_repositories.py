import os
import sqlite3
import tempfile
import unittest

from panel.repositories import TicketRepository, UserRepository


class UserRepositoryTestCase(unittest.TestCase):
    def setUp(self):
        handle = tempfile.NamedTemporaryFile(delete=False)
        handle.close()
        self.db_path = handle.name
        self.addCleanup(lambda: os.path.exists(self.db_path) and os.unlink(self.db_path))
        self._bootstrap_db()

    def _bootstrap_db(self):
        conn = sqlite3.connect(self.db_path)
        conn.execute(
            """
            CREATE TABLE users(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                username TEXT,
                password TEXT,
                password_hash TEXT,
                role TEXT,
                role_id INTEGER,
                is_blocked INTEGER DEFAULT 0
            )
            """
        )
        conn.execute(
            """
            CREATE TABLE roles(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT,
                permissions TEXT
            )
            """
        )
        conn.execute("INSERT INTO roles(name, permissions) VALUES (?, ?)", ("admin", '{"pages": ["*"]}'))
        conn.execute(
            "INSERT INTO users(username, password, role_id, is_blocked) VALUES (?, ?, ?, ?)",
            ("bender", "secret", 1, 0),
        )
        conn.commit()
        conn.close()

    def test_find_by_username(self):
        repo = UserRepository(self.db_path)
        user = repo.find_by_username("BENDER")
        self.assertIsNotNone(user)
        self.assertEqual(user["username"], "bender")
        self.assertEqual(user["role_name"], "admin")


class TicketRepositoryTestCase(unittest.TestCase):
    def setUp(self):
        handle = tempfile.NamedTemporaryFile(delete=False)
        handle.close()
        self.db_path = handle.name
        self.addCleanup(lambda: os.path.exists(self.db_path) and os.unlink(self.db_path))
        self._bootstrap_db()

    def _bootstrap_db(self):
        conn = sqlite3.connect(self.db_path)
        conn.execute(
            """
            CREATE TABLE tickets(
                ticket_id TEXT PRIMARY KEY,
                user_id INTEGER,
                status TEXT,
                resolved_at TEXT,
                resolved_by TEXT,
                channel_id INTEGER,
                reopen_count INTEGER,
                closed_count INTEGER
            )
            """
        )
        rows = [
            ("T-1", 1, "pending", None, None, 10, 0, 0),
            ("T-2", 2, "resolved", "2024-01-01", "admin", 12, 1, 1),
        ]
        conn.executemany(
            "INSERT INTO tickets VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            rows,
        )
        conn.commit()
        conn.close()

    def test_list_recent(self):
        repo = TicketRepository(self.db_path)
        data = repo.list_recent(limit=5)
        self.assertEqual(len(data), 2)
        self.assertEqual(data[0]["ticket_id"], "T-2")

    def test_count_by_status(self):
        repo = TicketRepository(self.db_path)
        summary = repo.count_by_status()
        self.assertEqual(summary["pending"], 1)
        self.assertEqual(summary["resolved"], 1)


if __name__ == "__main__":  # pragma: no cover
    unittest.main()

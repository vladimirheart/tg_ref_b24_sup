import os
import sqlite3
import tempfile
import unittest

from werkzeug.security import generate_password_hash

from panel.repositories import UserRepository
from panel.services import AuthService, AuthenticationError, SettingsService, TicketService
from panel.storage import SettingsStorage


class AuthServiceTestCase(unittest.TestCase):
    def setUp(self):
        handle = tempfile.NamedTemporaryFile(delete=False)
        handle.close()
        self.db_path = handle.name
        self.addCleanup(lambda: os.path.exists(self.db_path) and os.unlink(self.db_path))
        self._bootstrap_db()
        self.service = AuthService(UserRepository(self.db_path))

    def _bootstrap_db(self):
        conn = sqlite3.connect(self.db_path)
        conn.execute(
            """
            CREATE TABLE roles(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT,
                permissions TEXT
            )
            """
        )
        conn.execute(
            """
            CREATE TABLE users(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                username TEXT,
                password TEXT,
                password_hash TEXT,
                role_id INTEGER,
                is_blocked INTEGER DEFAULT 0
            )
            """
        )
        conn.execute("INSERT INTO roles(name, permissions) VALUES (?, ?)", ("support", '{"pages": ["tickets"]}'))
        conn.execute(
            "INSERT INTO users(username, password_hash, role_id, is_blocked) VALUES (?, ?, ?, ?)",
            ("leela", generate_password_hash("nibbler"), 1, 0),
        )
        conn.execute(
            "INSERT INTO users(username, password, role_id, is_blocked) VALUES (?, ?, ?, ?)",
            ("fry", "pizza", 1, 1),
        )
        conn.commit()
        conn.close()

    def test_authenticate_success(self):
        result = self.service.authenticate("leela", "nibbler")
        self.assertEqual(result.username, "leela")
        payload = AuthService.build_session_payload(result)
        self.assertTrue(payload["logged_in"])

    def test_blocked_user_fails(self):
        with self.assertRaises(AuthenticationError):
            self.service.authenticate("fry", "pizza")


class TicketServiceTestCase(unittest.TestCase):
    class DummyRepo:
        def __init__(self):
            self.calls = 0

        def list_recent(self, limit):
            self.calls += 1
            return [{"ticket_id": str(i), "status": "pending"} for i in range(limit)]

        def count_by_status(self):
            return {"pending": 3}

    def test_cache_refresh(self):
        repo = self.DummyRepo()
        service = TicketService(repo)
        first = service.list_recent(limit=2)
        second = service.list_recent(limit=2)
        self.assertEqual(repo.calls, 1)
        self.assertEqual(len(first), len(second))
        service.refresh_cache()
        self.assertEqual(repo.calls, 2)


class SettingsServiceTestCase(unittest.TestCase):
    def setUp(self):
        handle = tempfile.NamedTemporaryFile(delete=False)
        handle.close()
        self.path = handle.name
        self.addCleanup(lambda: os.path.exists(self.path) and os.unlink(self.path))
        self.storage = SettingsStorage(self.path)
        self.service = SettingsService(self.storage)

    def test_update_overrides_fields(self):
        self.service.update({"timezone": "Europe/Moscow"})
        data = self.service.load()
        self.assertEqual(data["timezone"], "Europe/Moscow")


if __name__ == "__main__":  # pragma: no cover
    unittest.main()

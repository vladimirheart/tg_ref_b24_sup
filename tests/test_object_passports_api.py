import importlib
import os
import tempfile
import unittest


class ObjectPassportsApiTestCase(unittest.TestCase):
    def setUp(self):
        fd, self.db_path = tempfile.mkstemp()
        os.close(fd)
        self._cleanup_files = [self.db_path]
        os.environ["TELEGRAM_BOT_TOKEN"] = "dummy-token"
        os.environ["APP_DB_OBJECT_PASSPORTS"] = self.db_path
        # Reload module to pick up fresh settings with the temp DB path.
        from panel import app as panel_app

        importlib.reload(panel_app)
        self.panel_app = panel_app
        self.client = self.panel_app.app.test_client()
        with self.client.session_transaction() as session:
            session["user_email"] = "tester@example.com"

    def tearDown(self):
        for key in ("TELEGRAM_BOT_TOKEN", "APP_DB_OBJECT_PASSPORTS"):
            os.environ.pop(key, None)
        for path in self._cleanup_files:
            if os.path.exists(path):
                os.unlink(path)

    def test_equipment_filter_request_returns_empty_list(self):
        response = self.client.get("/api/object_passports?equipment_type=router")
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json, {"items": []})


if __name__ == "__main__":  # pragma: no cover
    unittest.main()

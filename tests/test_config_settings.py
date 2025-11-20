"""Tests for the typed configuration loader."""
from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from config.settings import Settings


def _base_env() -> dict[str, str]:
    return {"TELEGRAM_BOT_TOKEN": "test-token", "GROUP_CHAT_ID": "12345"}


class SettingsTests(unittest.TestCase):
    def test_shared_settings_loaded(self):
        settings = Settings.load(env_overrides=_base_env())
        data = settings.shared.load_settings()
        self.assertIsInstance(data, dict)
        self.assertIn("dialog_config", data)
        self.assertEqual(settings.integrations.telegram.group_chat_id, 12345)
        self.assertTrue(settings.db.tickets_path.name.endswith("tickets.db"))

    def test_group_chat_id_optional(self):
        settings = Settings.load(env_overrides={"TELEGRAM_BOT_TOKEN": "test-token"})
        self.assertIsNone(settings.integrations.telegram.group_chat_id)

    def test_storage_directories_created_from_env(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            attachments = Path(tmpdir) / "files"
            overrides = _base_env()
            overrides["APP_STORAGE_ATTACHMENTS"] = str(attachments)
            settings = Settings.load(env_overrides=overrides)
            self.assertEqual(settings.storage.attachments, attachments)
            self.assertTrue(attachments.exists())
            self.assertTrue((attachments / "knowledge_base").exists())
            self.assertTrue((attachments / "avatars" / "history").exists())


if __name__ == "__main__":  # pragma: no cover
    unittest.main()

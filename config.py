import json
import logging
import os
from functools import lru_cache
from pathlib import Path

from dotenv import load_dotenv

# Загружаем переменные окружения из .env в корне
BASE_DIR = Path(__file__).parent.absolute()
load_dotenv(BASE_DIR / ".env")

# Пути к базам данных и настройкам
DB_PATH = BASE_DIR / "tickets.db"
USERS_DB_PATH = BASE_DIR / "users.db"
SETTINGS_PATH = BASE_DIR / "settings.json"


def _normalize_database_url(raw_url: str | None) -> str:
    """Normalize database URLs and provide a SQLite fallback."""
    if not raw_url:
        return f"sqlite:///{DB_PATH}"
    if raw_url.startswith("postgres://"):
        # SQLAlchemy expects the explicit driver specification
        raw_url = raw_url.replace("postgres://", "postgresql+psycopg://", 1)
    return raw_url


DATABASE_URL = _normalize_database_url(os.environ.get("DATABASE_URL"))

# Telegram настройки из .env
TELEGRAM_BOT_TOKEN = os.environ.get("TELEGRAM_BOT_TOKEN")
GROUP_CHAT_ID = os.environ.get("GROUP_CHAT_ID")
SECRET_KEY = os.environ.get("SECRET_KEY")

# Проверки
if not TELEGRAM_BOT_TOKEN:
    raise ValueError("❌ TELEGRAM_BOT_TOKEN не установлен в .env")

if not GROUP_CHAT_ID:
    raise ValueError("❌ GROUP_CHAT_ID не установлен в .env")

# Преобразуем GROUP_CHAT_ID в int
try:
    GROUP_CHAT_ID = int(GROUP_CHAT_ID)
except ValueError:
    raise ValueError("❌ GROUP_CHAT_ID должен быть числом")


@lru_cache(maxsize=1)
def load_settings() -> dict:
    """Загрузка настроек из settings.json с кешированием."""
    try:
        with open(SETTINGS_PATH, "r", encoding="utf-8") as f:
            return json.load(f)
    except Exception as exc:
        logging.error("Ошибка загрузки settings.json: %s", exc)
        return {}

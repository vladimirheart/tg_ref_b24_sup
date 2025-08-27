import os
import json
import logging
from pathlib import Path
from dotenv import load_dotenv

# Загружаем переменные окружения из .env в корне
BASE_DIR = Path(__file__).parent.absolute()
load_dotenv(BASE_DIR / '.env')

# Пути к базам данных и настройкам
DB_PATH = BASE_DIR / "tickets.db"
USERS_DB_PATH = BASE_DIR / "users.db"
SETTINGS_PATH = BASE_DIR / "settings.json"

# Telegram настройки из .env
TELEGRAM_BOT_TOKEN = os.environ.get('TELEGRAM_BOT_TOKEN')
GROUP_CHAT_ID = os.environ.get('GROUP_CHAT_ID')
SECRET_KEY = os.environ.get('SECRET_KEY')

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


def load_settings():
    """Загрузка настроек из settings.json."""
    try:
        with open(SETTINGS_PATH, "r", encoding="utf-8") as f:
            return json.load(f)
    except Exception as exc:
        logging.error(f"Ошибка загрузки settings.json: {exc}")
        return {}

"""Typed runtime configuration powered by Dynaconf and Pydantic."""
from __future__ import annotations

import json
import logging
import os
from pathlib import Path
from typing import Any, Iterable

from dotenv import load_dotenv as _load_dotenv
try:  # pragma: no cover - fallback used only when dynaconf is absent
    from dynaconf import Dynaconf as _Dynaconf
except Exception:  # pragma: no cover
    class _Dynaconf:  # type: ignore[too-many-ancestors]
        def __init__(self, *, load_dotenv: bool = True, dotenv_path: str | None = None, **_: Any) -> None:
            self._values: dict[str, Any] = {}
            if load_dotenv:
                _load_dotenv(dotenv_path)

        def get(self, key: str, default: Any | None = None) -> Any | None:
            if key in self._values:
                return self._values[key]
            return os.environ.get(key, default)

        def set(self, key: str, value: Any) -> None:
            self._values[key] = value

    Dynaconf = _Dynaconf
else:  # pragma: no cover - executed when real dynaconf is available
    Dynaconf = _Dynaconf

try:  # pragma: no cover - executed when pydantic is installed
    from pydantic import BaseModel as _BaseModel
except Exception:  # pragma: no cover
    class _BaseModel:
        """Very small subset of pydantic.BaseModel used for local tests."""

        def __init__(self, **data: Any) -> None:
            self._apply_defaults()
            for key, value in data.items():
                setattr(self, key, value)

        def _apply_defaults(self) -> None:
            annotations = getattr(self, "__annotations__", {})
            for name in annotations:
                if hasattr(self.__class__, name):
                    value = getattr(self.__class__, name)
                    if isinstance(value, (dict, list, set)):
                        value = value.copy()
                else:
                    value = None
                setattr(self, name, value)

    BaseModel = _BaseModel
else:  # pragma: no cover
    BaseModel = _BaseModel

PROJECT_ROOT = Path(__file__).resolve().parent.parent
CONFIG_DIR = Path(__file__).resolve().parent
SHARED_DIR = CONFIG_DIR / "shared"
ENV_PATH = PROJECT_ROOT / ".env"
DEFAULT_ATTACHMENTS_DIR = PROJECT_ROOT / "attachments"
DEFAULT_KNOWLEDGE_BASE_DIR = DEFAULT_ATTACHMENTS_DIR / "knowledge_base"
DEFAULT_AVATARS_DIR = DEFAULT_ATTACHMENTS_DIR / "avatars"
DEFAULT_AVATAR_HISTORY_DIR = DEFAULT_AVATARS_DIR / "history"
DEFAULT_OBJECT_PASSPORT_UPLOADS = PROJECT_ROOT / "object_passport_uploads"
DEFAULT_USER_PHOTOS_DIR = PROJECT_ROOT / "panel" / "static" / "user_photos"
DEFAULT_TICKETS_DB = PROJECT_ROOT / "tickets.db"
DEFAULT_USERS_DB = PROJECT_ROOT / "users.db"
DEFAULT_BOT_DB = PROJECT_ROOT / "bot_database.db"
DEFAULT_OBJECT_PASSPORT_DB = PROJECT_ROOT / "object_passports.db"

logger = logging.getLogger(__name__)


def _create_env(dotenv_path: Path | None, overrides: dict[str, Any] | None = None) -> Dynaconf:
    options: dict[str, Any] = {
        "envvar_prefix": False,
        "environments": False,
        "load_dotenv": True,
    }
    if dotenv_path and dotenv_path.exists():
        options["dotenv_path"] = str(dotenv_path)
    env = Dynaconf(**options)
    if overrides:
        for key, value in overrides.items():
            env.set(key, value)
    return env


def _normalize_database_url(raw_url: str | None, default_path: Path) -> str:
    if not raw_url:
        return f"sqlite:///{default_path}"
    if raw_url.startswith("postgres://"):
        raw_url = raw_url.replace("postgres://", "postgresql+psycopg://", 1)
    return raw_url


class StorageSettings(BaseModel):
    """Filesystem layout used by bots and the panel."""

    attachments: Path = DEFAULT_ATTACHMENTS_DIR
    knowledge_base: Path = DEFAULT_KNOWLEDGE_BASE_DIR
    avatars: Path = DEFAULT_AVATARS_DIR
    avatar_history: Path = DEFAULT_AVATAR_HISTORY_DIR
    object_passport_uploads: Path = DEFAULT_OBJECT_PASSPORT_UPLOADS
    user_photos: Path = DEFAULT_USER_PHOTOS_DIR

    @classmethod
    def from_env(cls, env: Dynaconf) -> "StorageSettings":
        attachments = Path(env.get("APP_STORAGE_ATTACHMENTS") or DEFAULT_ATTACHMENTS_DIR)
        knowledge_base = Path(env.get("APP_STORAGE_KNOWLEDGE_BASE") or (attachments / "knowledge_base"))
        avatars = Path(env.get("APP_STORAGE_AVATARS") or (attachments / "avatars"))
        avatar_history = Path(env.get("APP_STORAGE_AVATAR_HISTORY") or (avatars / "history"))
        object_passport_uploads = Path(
            env.get("APP_STORAGE_OBJECT_PASSPORTS")
            or DEFAULT_OBJECT_PASSPORT_UPLOADS
        )
        user_photos = Path(env.get("APP_STORAGE_USER_PHOTOS") or DEFAULT_USER_PHOTOS_DIR)
        return cls(
            attachments=attachments,
            knowledge_base=knowledge_base,
            avatars=avatars,
            avatar_history=avatar_history,
            object_passport_uploads=object_passport_uploads,
            user_photos=user_photos,
        )

    def ensure_directories(self) -> None:
        for path in self.iter_directories():
            path.mkdir(parents=True, exist_ok=True)

    def iter_directories(self) -> Iterable[Path]:
        yield self.attachments
        yield self.knowledge_base
        yield self.avatars
        yield self.avatar_history
        yield self.object_passport_uploads
        yield self.user_photos


class DatabaseSettings(BaseModel):
    """Database-related configuration and normalized URLs."""

    tickets_path: Path = DEFAULT_TICKETS_DB
    users_path: Path = DEFAULT_USERS_DB
    bot_path: Path = DEFAULT_BOT_DB
    object_passports_path: Path = DEFAULT_OBJECT_PASSPORT_DB
    url: str = ""

    @classmethod
    def from_env(cls, env: Dynaconf) -> "DatabaseSettings":
        tickets = Path(env.get("APP_DB_TICKETS") or DEFAULT_TICKETS_DB)
        users = Path(env.get("APP_DB_USERS") or DEFAULT_USERS_DB)
        bot_db = Path(env.get("APP_DB_BOT") or DEFAULT_BOT_DB)
        object_passports = Path(env.get("APP_DB_OBJECT_PASSPORTS") or DEFAULT_OBJECT_PASSPORT_DB)
        url = _normalize_database_url(env.get("DATABASE_URL"), tickets)
        return cls(
            tickets_path=tickets,
            users_path=users,
            bot_path=bot_db,
            object_passports_path=object_passports,
            url=url,
        )


class SharedConfigFiles(BaseModel):
    """Helpers for reading JSON configs stored in config/shared."""

    settings_path: Path = SHARED_DIR / "settings.json"
    locations_path: Path = SHARED_DIR / "locations.json"
    org_structure_path: Path = SHARED_DIR / "org_structure.json"

    @staticmethod
    def _load_json(path: Path) -> dict[str, Any]:
        try:
            with path.open("r", encoding="utf-8") as handle:
                return json.load(handle)
        except FileNotFoundError:
            logger.warning("Shared config file %s is missing", path)
        except Exception as exc:
            logger.error("Failed to read shared config %s: %s", path, exc)
        return {}

    def load_settings(self) -> dict[str, Any]:
        return self._load_json(self.settings_path)

    def load_locations(self) -> dict[str, Any]:
        return self._load_json(self.locations_path)

    def load_org_structure(self) -> dict[str, Any]:
        return self._load_json(self.org_structure_path)


class TelegramSettings(BaseModel):
    token: str
    group_chat_id: int

    @classmethod
    def from_env(cls, env: Dynaconf) -> "TelegramSettings":
        token = env.get("TELEGRAM_BOT_TOKEN")
        if not token:
            raise ValueError("TELEGRAM_BOT_TOKEN is not configured")
        group_chat_id = env.get("GROUP_CHAT_ID")
        if group_chat_id is None:
            raise ValueError("GROUP_CHAT_ID is not configured")
        try:
            group_chat_id_int = int(group_chat_id)
        except (TypeError, ValueError) as exc:
            raise ValueError("GROUP_CHAT_ID must be an integer") from exc
        return cls(token=token, group_chat_id=group_chat_id_int)


class IntegrationsSettings(BaseModel):
    telegram: TelegramSettings

    @classmethod
    def from_env(cls, env: Dynaconf) -> "IntegrationsSettings":
        return cls(telegram=TelegramSettings.from_env(env))


class SecuritySettings(BaseModel):
    secret_key: str = "dev-secret-key"

    @classmethod
    def from_env(cls, env: Dynaconf) -> "SecuritySettings":
        secret = env.get("SECRET_KEY")
        if not secret:
            secret = "dev-secret-key"
        return cls(secret_key=str(secret))


class Settings(BaseModel):
    """Aggregate configuration shared between services."""

    project_root: Path = PROJECT_ROOT
    config_dir: Path = CONFIG_DIR
    storage: StorageSettings
    db: DatabaseSettings
    shared: SharedConfigFiles
    integrations: IntegrationsSettings
    security: SecuritySettings

    @classmethod
    def load(cls, *, env_overrides: dict[str, Any] | None = None) -> "Settings":
        env = _create_env(ENV_PATH if ENV_PATH.exists() else None, overrides=env_overrides)
        storage = StorageSettings.from_env(env)
        db = DatabaseSettings.from_env(env)
        shared = SharedConfigFiles()
        integrations = IntegrationsSettings.from_env(env)
        security = SecuritySettings.from_env(env)
        instance = cls(
            storage=storage,
            db=db,
            shared=shared,
            integrations=integrations,
            security=security,
        )
        instance.storage.ensure_directories()
        return instance


__all__ = [
    "Settings",
    "StorageSettings",
    "DatabaseSettings",
    "SharedConfigFiles",
    "IntegrationsSettings",
    "SecuritySettings",
    "TelegramSettings",
]

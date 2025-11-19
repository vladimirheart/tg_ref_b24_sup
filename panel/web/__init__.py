"""Flask application factory for the operator panel."""
from __future__ import annotations

import os
from pathlib import Path

from flask import Flask

from panel.background import init_background
from panel.repositories import TicketRepository, UserRepository
from panel.services import AuthService, SettingsService, TicketService
from panel.storage import SettingsStorage
from shared_config import shared_config_path

from . import auth, settings as settings_bp, tickets

PROJECT_ROOT = Path(__file__).resolve().parents[1]
TEMPLATE_FOLDER = PROJECT_ROOT / "templates"
STATIC_FOLDER = PROJECT_ROOT / "static"


def create_app(config: dict | None = None) -> Flask:
    app = Flask(
        __name__,
        template_folder=str(TEMPLATE_FOLDER),
        static_folder=str(STATIC_FOLDER),
    )
    default_config = {
        "SECRET_KEY": os.getenv("SECRET_KEY", "dev"),
        "USERS_DB_PATH": str(PROJECT_ROOT.parent / "users.db"),
        "TICKETS_DB_PATH": str(PROJECT_ROOT / "tickets.db"),
        "SETTINGS_FILE_PATH": str(shared_config_path("settings.json")),
        "ENABLE_BACKGROUND_TASKS": True,
    }
    app.config.from_mapping(default_config)
    if config:
        app.config.update(config)

    _init_services(app)

    app.register_blueprint(auth.bp)
    app.register_blueprint(tickets.bp)
    app.register_blueprint(settings_bp.bp)

    if app.config.get("ENABLE_BACKGROUND_TASKS"):
        init_background(app)
    return app


def _init_services(app: Flask) -> None:
    services = {}
    user_repo = UserRepository(app.config["USERS_DB_PATH"])
    ticket_repo = TicketRepository(app.config["TICKETS_DB_PATH"])
    settings_storage = SettingsStorage(app.config["SETTINGS_FILE_PATH"])

    services["auth"] = AuthService(user_repo)
    services["tickets"] = TicketService(ticket_repo)
    services["settings"] = SettingsService(settings_storage)

    app.extensions.setdefault("services", {}).update(services)

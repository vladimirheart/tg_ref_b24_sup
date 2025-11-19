"""Blueprint exposing runtime settings endpoints."""
from __future__ import annotations

from flask import Blueprint, jsonify, request

from panel.services import SettingsService
from .decorators import login_required
from .dependencies import get_service

bp = Blueprint("settings", __name__, url_prefix="/settings")


def _settings_service() -> SettingsService:
    return get_service("settings")


@bp.route("/runtime", methods=["GET"])
@login_required
def get_runtime_settings():
    return jsonify({"settings": _settings_service().load()})


@bp.route("/runtime", methods=["POST"])
@login_required
def update_runtime_settings():
    payload = request.get_json(silent=True) or {}
    updated = _settings_service().update(payload)
    return jsonify({"settings": updated})

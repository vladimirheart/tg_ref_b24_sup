"""Shared Flask decorators used by blueprints."""
from __future__ import annotations

from functools import wraps
from flask import request, session, redirect, url_for, jsonify


def login_required(fn):
    @wraps(fn)
    def wrapper(*args, **kwargs):
        if not session.get("logged_in"):
            if request.accept_mimetypes.best == "application/json":
                return jsonify({"authenticated": False, "error": "auth_required"}), 401
            return redirect(url_for("auth.login", next=request.url))
        return fn(*args, **kwargs)

    return wrapper

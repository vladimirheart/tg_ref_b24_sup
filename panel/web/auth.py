"""Blueprint exposing authentication endpoints."""
from __future__ import annotations

from flask import (
    Blueprint,
    jsonify,
    redirect,
    render_template,
    request,
    session,
    url_for,
)

from panel.services import AuthService, AuthenticationError
from .dependencies import get_service

bp = Blueprint("auth", __name__)


def _auth_service() -> AuthService:
    return get_service("auth")


@bp.route("/login", methods=["GET", "POST"])
def login():
    if request.method == "POST":
        username = (request.form.get("username") or "").strip()
        password = request.form.get("password") or ""
        try:
            result = _auth_service().authenticate(username, password)
        except AuthenticationError as exc:
            return render_template("login.html", error=str(exc)), 401
        session.clear()
        session.update(AuthService.build_session_payload(result))
        session.setdefault("user_email", result.username)
        next_url = request.args.get("next") or url_for("tickets.tickets_dashboard")
        return redirect(next_url)
    return render_template("login.html", error=None)


@bp.route("/logout")
def logout():
    session.clear()
    return redirect(url_for("auth.login"))


@bp.route("/api/whoami")
def whoami():
    if not session.get("logged_in"):
        return jsonify({"authenticated": False}), 401
    service = _auth_service()
    user = service.resolve_user(session.get("user_id"), session.get("username"))
    if not user:
        return jsonify({"authenticated": False}), 401
    return jsonify(
        {
            "authenticated": True,
            "user": {
                "id": user.get("id"),
                "username": user.get("username"),
                "role": user.get("role_name") or user.get("role"),
            },
        }
    )


@bp.route("/api/ping_auth")
def ping_auth():
    return jsonify({"authenticated": bool(session.get("logged_in"))})

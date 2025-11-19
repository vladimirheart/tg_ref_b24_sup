"""Blueprint with lightweight ticket endpoints."""
from __future__ import annotations

from flask import Blueprint, jsonify, render_template, request

from panel.services import TicketService
from .decorators import login_required
from .dependencies import get_service

bp = Blueprint("tickets", __name__)


def _ticket_service() -> TicketService:
    return get_service("tickets")


@bp.route("/")
@login_required
def tickets_dashboard():
    service = _ticket_service()
    tickets = service.list_recent(limit=10)
    summary = service.status_summary()
    return render_template(
        "tickets_overview.html",
        tickets=tickets,
        summary=summary,
    )


@bp.route("/api/tickets")
@login_required
def tickets_api():
    limit = request.args.get("limit", type=int) or 20
    tickets = _ticket_service().list_recent(limit=limit)
    return jsonify({"tickets": tickets})


@bp.route("/api/tickets/summary")
@login_required
def tickets_summary_api():
    return jsonify({"summary": _ticket_service().status_summary()})

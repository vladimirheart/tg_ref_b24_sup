"""Background task initialisation for the panel."""
from __future__ import annotations

import atexit
from apscheduler.schedulers.background import BackgroundScheduler


def init_background(app):
    """Start background jobs once the Flask app is ready."""
    scheduler = BackgroundScheduler(timezone="UTC")

    ticket_service = app.extensions["services"]["tickets"]

    scheduler.add_job(
        ticket_service.refresh_cache,
        "interval",
        minutes=5,
        id="tickets-cache-refresh",
        replace_existing=True,
    )

    scheduler.start()
    atexit.register(lambda: scheduler.shutdown(wait=False))
    app.extensions["scheduler"] = scheduler
    return scheduler

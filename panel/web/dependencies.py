"""Helpers to access services within request handlers."""
from __future__ import annotations

from flask import current_app


def get_service(name: str):
    services = current_app.extensions.get("services", {})
    if name not in services:
        raise KeyError(f"Service '{name}' is not initialised")
    return services[name]

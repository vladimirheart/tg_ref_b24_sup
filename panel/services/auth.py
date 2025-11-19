"""Authentication related operations."""
from __future__ import annotations

import json
import secrets
from dataclasses import dataclass
from typing import Any

from werkzeug.security import check_password_hash


@dataclass(slots=True)
class AuthenticationResult:
    user_id: int
    username: str
    role: str | None
    role_id: int | None
    permissions: dict[str, Any]


class AuthenticationError(Exception):
    pass


class AuthService:
    def __init__(self, repository):
        self.repository = repository

    def authenticate(self, username: str, password: str) -> AuthenticationResult:
        if not username or not password:
            raise AuthenticationError("Имя пользователя и пароль обязательны")
        user = self.repository.find_by_username(username)
        if not user:
            raise AuthenticationError("Пользователь не найден")
        if user.get("is_blocked"):
            raise AuthenticationError("Пользователь заблокирован")
        if not self._check_password(user, password):
            raise AuthenticationError("Неверный пароль")
        return AuthenticationResult(
            user_id=user.get("id"),
            username=user.get("username") or username,
            role=user.get("role_name") or user.get("role"),
            role_id=user.get("role_id"),
            permissions=self._parse_permissions(user.get("role_permissions")),
        )

    def resolve_user(self, user_id: int | None = None, username: str | None = None):
        if user_id is not None:
            return self.repository.get_by_id(user_id)
        if username:
            return self.repository.find_by_username(username)
        return None

    @staticmethod
    def _parse_permissions(raw: str | None) -> dict[str, Any]:
        if not raw:
            return {}
        try:
            parsed = json.loads(raw)
        except json.JSONDecodeError:
            return {}
        return parsed if isinstance(parsed, dict) else {}

    @staticmethod
    def _check_password(user_row: dict[str, Any], candidate: str) -> bool:
        password_hash = (user_row.get("password_hash") or "").strip()
        if password_hash:
            try:
                if check_password_hash(password_hash, candidate):
                    return True
            except ValueError:
                pass
        stored_password = (user_row.get("password") or "").strip()
        if stored_password:
            return secrets.compare_digest(stored_password, candidate)
        return False

    @staticmethod
    def build_session_payload(result: AuthenticationResult) -> dict[str, Any]:
        return {
            "logged_in": True,
            "user_id": result.user_id,
            "user": result.username,
            "username": result.username,
            "role": result.role,
            "role_id": result.role_id,
            "permissions": result.permissions,
        }

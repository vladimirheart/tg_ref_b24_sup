"""Utilities for working with sensitive secrets such as bot tokens."""

from __future__ import annotations

import base64
import hashlib
import os
from functools import lru_cache

from cryptography.fernet import Fernet, InvalidToken


class SecretStorageError(RuntimeError):
    """Raised when encrypting or decrypting a secret fails."""


def _get_secret_source() -> str:
    """Return the raw secret key string used for encryption."""

    for env_key in ("PANEL_SECRET_KEY", "SECRET_KEY"):
        value = os.getenv(env_key)
        if value:
            return value
    raise SecretStorageError(
        "Не указан PANEL_SECRET_KEY/SECRET_KEY для шифрования токенов"
    )


@lru_cache(maxsize=1)
def _get_fernet() -> Fernet:
    """Build a cached Fernet instance using the configured secret."""

    secret = _get_secret_source().encode("utf-8")
    digest = hashlib.sha256(secret).digest()
    key = base64.urlsafe_b64encode(digest)
    return Fernet(key)


def encrypt_token(token: str) -> str:
    """Encrypt and sign a token string for safe storage."""

    if token is None:
        raise SecretStorageError("Нельзя шифровать пустой токен")
    token_bytes = token.encode("utf-8")
    encrypted = _get_fernet().encrypt(token_bytes)
    return encrypted.decode("utf-8")


def decrypt_token(encrypted_token: str) -> str:
    """Decrypt a previously encrypted token string."""

    if encrypted_token is None:
        raise SecretStorageError("Зашифрованный токен не найден")
    try:
        decrypted = _get_fernet().decrypt(encrypted_token.encode("utf-8"))
    except (InvalidToken, ValueError) as exc:
        raise SecretStorageError("Не удалось расшифровать токен") from exc
    return decrypted.decode("utf-8")


def mask_token(token: str | None, *, visible: int = 4) -> str:
    """Return a masked representation of a token for UI purposes."""

    if not token:
        return "—"
    prefix = token[:visible]
    suffix = token[-visible:] if len(token) > visible else ""
    return f"{prefix}{'•' * 6}{suffix}" if suffix else f"{prefix}••••"

"""Notification providers for different platforms."""

from __future__ import annotations

import logging
import os
from dataclasses import dataclass
from typing import Protocol

import requests
from vk_api import VkApi
from vk_api.exceptions import VkApiError

from .repositories import BotCredentialRepository
from .secrets import decrypt_token, SecretStorageError


log = logging.getLogger(__name__)


class ProviderError(RuntimeError):
    """Raised when a provider cannot deliver a message."""


class NotificationProvider(Protocol):
    def send(self, recipient: str, message: str, *, extra: dict | None = None) -> None:
        ...


def _is_dry_run() -> bool:
    raw = os.getenv("NOTIFICATIONS_DRY_RUN")
    if raw is None:
        return True
    return raw.strip().lower() not in {"0", "false", "no"}


@dataclass(slots=True)
class TelegramProvider:
    token: str
    dry_run: bool = True

    def send(self, recipient: str, message: str, *, extra: dict | None = None) -> None:
        if self.dry_run:
            log.info("[DryRun][Telegram] %s -> %s", recipient, message)
            return
        if not recipient:
            raise ProviderError("chat_id обязателен")
        response = requests.post(
            f"https://api.telegram.org/bot{self.token}/sendMessage",
            json={
                "chat_id": recipient,
                "text": message,
                "disable_web_page_preview": True,
            },
            timeout=15,
        )
        data = response.json() if response.content else {}
        if not response.ok or not data.get("ok", False):
            raise ProviderError(data.get("description") or response.text)


@dataclass(slots=True)
class VKProvider:
    token: str
    dry_run: bool = True

    def send(self, recipient: str, message: str, *, extra: dict | None = None) -> None:
        if self.dry_run:
            log.info("[DryRun][VK] %s -> %s", recipient, message)
            return
        group_id = None
        if extra:
            group_id = extra.get("group_id") or extra.get("groupId")
        if not group_id:
            raise ProviderError("group_id обязателен для VK")
        try:
            vk_session = VkApi(token=self.token)
            api = vk_session.get_api()
            api.messages.send(
                random_id=0,
                peer_id=int(recipient),
                message=message,
                group_id=int(group_id),
            )
        except (VkApiError, ValueError) as exc:
            raise ProviderError(str(exc))


class ProviderFactory:
    def __init__(self, credentials_repo: BotCredentialRepository):
        self.credentials_repo = credentials_repo

    def build(self, platform: str, credential_id: int | None, *, extra: dict | None = None) -> NotificationProvider:
        if not credential_id:
            raise ProviderError("Для канала не выбран набор учётных данных")
        credential = self.credentials_repo.get(credential_id)
        if not credential:
            raise ProviderError("Учётные данные не найдены")
        try:
            token = decrypt_token(credential.encrypted_token)
        except SecretStorageError as exc:
            raise ProviderError(str(exc))
        dry_run = _is_dry_run()
        platform = (platform or "telegram").strip().lower()
        if platform == "telegram":
            return TelegramProvider(token=token, dry_run=dry_run)
        if platform == "vk":
            return VKProvider(token=token, dry_run=dry_run)
        raise ProviderError(f"Неизвестная платформа: {platform}")

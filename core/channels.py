"""Service layer for channel operations shared by panel and bots."""
from __future__ import annotations

import json
import logging
import secrets
from typing import Any

from .dto import ChannelBotConfig, ChannelDTO, ChannelQuestionnaire
from .models import Channel
from .repositories.channels import BotCredentialRepository, ChannelRepository
from .secrets import SecretStorageError, decrypt_token

logger = logging.getLogger(__name__)


def _parse_json(value: Any) -> dict:
    if not value:
        return {}
    if isinstance(value, dict):
        return value
    try:
        parsed = json.loads(value)
    except (TypeError, json.JSONDecodeError):
        return {}
    return parsed if isinstance(parsed, dict) else {}


class ChannelService:
    """High level orchestration for channel access."""

    def __init__(
        self,
        *,
        channel_repo: ChannelRepository | None = None,
        credential_repo: BotCredentialRepository | None = None,
    ) -> None:
        self._channels = channel_repo or ChannelRepository()
        self._credentials = credential_repo or BotCredentialRepository()
        self._token_cache: dict[str, int] = {}

    def list_channels(self) -> list[ChannelDTO]:
        return [self._to_dto(channel) for channel in self._channels.list()]

    def get_channel(self, channel_id: int) -> ChannelDTO | None:
        channel = self._channels.get(channel_id)
        if channel is None:
            return None
        return self._to_dto(channel)

    def get_questionnaire(self, channel_id: int) -> ChannelQuestionnaire | None:
        channel = self._channels.get(channel_id)
        if channel is None:
            return None
        return ChannelQuestionnaire(
            channel_id=channel.id,
            question_template_id=(channel.question_template_id or "").strip() or None,
            rating_template_id=(channel.rating_template_id or "").strip() or None,
            questions_cfg=_parse_json(channel.questions_cfg),
            max_questions=int(channel.max_questions or 0),
        )

    def iter_active_bot_channels(self, platform: str | None = None) -> list[ChannelBotConfig]:
        configs: list[ChannelBotConfig] = []
        for channel in self._channels.list_active(platform):
            credential = channel.credential
            if credential is None and channel.credential_id:
                credential = self._credentials.get(channel.credential_id)
            if credential is None or not credential.encrypted_token:
                continue
            try:
                token = decrypt_token(credential.encrypted_token)
            except SecretStorageError as exc:
                logger.warning("Не удалось расшифровать токен канала %s: %s", channel.id, exc)
                continue
            self._token_cache[token] = channel.id
            configs.append(
                ChannelBotConfig(
                    id=channel.id,
                    token=token,
                    platform=channel.platform,
                    platform_config=_parse_json(channel.platform_config),
                )
            )
        return configs

    def get_channel_id_by_token(self, token: str) -> int:
        if token in self._token_cache:
            return self._token_cache[token]
        self._refresh_token_cache()
        if token not in self._token_cache:
            raise RuntimeError("Token не найден в секретном хранилище")
        return self._token_cache[token]

    def get_support_chat_id(self, channel_id: int, fallback: Any = None) -> Any:
        dto = self.get_channel(channel_id)
        if dto and dto.support_chat_id:
            value = dto.support_chat_id
            try:
                return int(value)
            except (TypeError, ValueError):
                return value
        return fallback

    def set_support_chat_id(self, channel_id: int, chat_id: Any) -> None:
        if not chat_id:
            return
        value = str(chat_id).strip()
        if not value:
            return
        try:
            self._channels.update(channel_id, {"support_chat_id": value})
        except Exception as exc:  # pragma: no cover - defensive logging
            logger.warning("Не удалось сохранить support_chat_id для канала %s: %s", channel_id, exc)

    def list_auto_close_templates(self) -> dict[int, str | None]:
        mapping: dict[int, str | None] = {}
        for channel in self._channels.list():
            mapping[channel.id] = channel.auto_action_template_id
        return mapping

    def ensure_public_id(self, desired: str | None = None) -> str:
        candidate = (desired or "").strip().lower()
        if candidate and not self._channels.public_id_exists(candidate):
            return candidate
        while True:
            generated = secrets.token_hex(8)
            if not self._channels.public_id_exists(generated):
                return generated

    def _refresh_token_cache(self) -> None:
        self._token_cache.clear()
        for channel_id, encrypted in self._channels.list_with_tokens():
            if not encrypted:
                continue
            try:
                token = decrypt_token(encrypted)
            except SecretStorageError:
                continue
            self._token_cache[token] = channel_id

    @staticmethod
    def _to_dto(channel: Channel) -> ChannelDTO:
        settings = _parse_json(channel.delivery_settings) or _parse_json(channel.platform_config)
        return ChannelDTO(
            id=channel.id,
            name=channel.channel_name,
            description=channel.description,
            platform=channel.platform,
            credential_id=channel.credential_id,
            public_id=channel.public_id,
            settings=settings,
            filters=_parse_json(channel.filters),
            is_active=bool(channel.is_active),
            support_chat_id=channel.support_chat_id,
            question_template_id=channel.question_template_id,
            rating_template_id=channel.rating_template_id,
            questions_cfg=_parse_json(channel.questions_cfg),
            max_questions=int(channel.max_questions or 0),
            auto_action_template_id=channel.auto_action_template_id,
        )

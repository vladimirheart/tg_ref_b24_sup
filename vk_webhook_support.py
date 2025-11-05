"""Webhook server for processing VK Callback API events.

The implementation shares the same dialog manager with the long-poll runner and
allows to expose an HTTP endpoint compatible with the Callback API. It is
inspired by projects like https://github.com/node-vk-bot-api/node-vk-bot-api
which support both longpoll and webhook approaches.
"""
from __future__ import annotations

import logging
import threading
from dataclasses import dataclass
from typing import Dict, Optional

from flask import Flask, Response, jsonify, request
from vk_api import VkApi

from bot_support import iter_active_channels
from vk_bot_support import VkConversationManager, _parse_platform_config

logger = logging.getLogger(__name__)


@dataclass
class VkWebhookChannel:
    """Holds configuration for a VK channel served via webhooks."""

    channel_id: int
    token: str
    group_id: int
    confirmation_token: Optional[str]
    secret: Optional[str]
    manager: VkConversationManager


class VkWebhookRegistry:
    """Keeps track of webhook-aware channels and their VK API clients."""

    def __init__(self) -> None:
        self._lock = threading.RLock()
        self._channels_by_group: Dict[int, VkWebhookChannel] = {}

    def load_from_db(self) -> None:
        """Refreshes registry from the shared channels table."""

        with self._lock:
            self._channels_by_group.clear()
            rows = iter_active_channels(platform="vk")
            if not rows:
                logger.warning("В БД нет активных VK-каналов для вебхуков")
                return
            for row in rows:
                row_dict = dict(row) if hasattr(row, "keys") else dict(row or {})
                config = _parse_platform_config(row_dict.get("platform_config"))
                group_id = config.get("group_id") or config.get("groupId")
                confirmation_token = config.get("confirmation_token") or config.get("confirmationToken")
                secret = config.get("secret") or config.get("callback_secret")
                if not group_id:
                    logger.warning("VK-канал %s пропущен: не указан group_id", row_dict.get("id"))
                    continue
                try:
                    group_id_int = int(group_id)
                except (TypeError, ValueError):
                    logger.warning(
                        "VK-канал %s пропущен: group_id должен быть числом, получено %r",
                        row_dict.get("id"),
                        group_id,
                    )
                    continue
                if not confirmation_token:
                    logger.warning(
                        "VK-канал %s пропущен: не задан confirmation_token для Callback API",
                        row_dict.get("id"),
                    )
                    continue
                token = row_dict.get("token")
                if not token:
                    logger.warning("VK-канал %s пропущен: не найден токен", row_dict.get("id"))
                    continue
                session = VkApi(token=token)
                api = session.get_api()
                manager = VkConversationManager(row_dict.get("id"), api, group_id=group_id_int)
                channel = VkWebhookChannel(
                    channel_id=row_dict.get("id"),
                    token=token,
                    group_id=group_id_int,
                    confirmation_token=confirmation_token,
                    secret=secret,
                    manager=manager,
                )
                self._channels_by_group[group_id_int] = channel
                logger.info(
                    "Вебхук для VK-канала %s готов (group_id=%s, confirmation_token=%s)",
                    row_dict.get("id"),
                    group_id_int,
                    confirmation_token,
                )

    def get(self, group_id: int) -> Optional[VkWebhookChannel]:
        with self._lock:
            return self._channels_by_group.get(group_id)

    def all_group_ids(self) -> Dict[int, VkWebhookChannel]:
        with self._lock:
            return dict(self._channels_by_group)


registry = VkWebhookRegistry()
app = Flask(__name__)


@app.route("/healthz", methods=["GET"])
def healthcheck() -> Response:
    payload = {"status": "ok", "vk_groups": list(registry.all_group_ids().keys())}
    return jsonify(payload)


@app.route("/webhooks/vk/<int:group_id>", methods=["POST"])
def handle_vk_event(group_id: int):
    channel = registry.get(group_id)
    if channel is None:
        logger.warning("Получен вебхук для неизвестного group_id %s", group_id)
        return Response("not registered", status=404)

    data = request.get_json(silent=True) or {}
    payload_type = data.get("type")
    if payload_type == "confirmation":
        logger.info("Запрос подтверждения вебхука для group_id=%s", group_id)
        return Response(channel.confirmation_token or "", status=200)

    secret = data.get("secret")
    if channel.secret and secret != channel.secret:
        logger.warning(
            "Отклонён вебхук для group_id=%s: неверный секрет (ожидалось %r, получено %r)",
            group_id,
            channel.secret,
            secret,
        )
        return Response("forbidden", status=403)

    if payload_type == "message_new":
        message = (data.get("object") or {}).get("message") or {}
        channel.manager.handle_message(message)
        return Response("ok", status=200)

    logger.debug("Игнорируем событие %s для group_id=%s", payload_type, group_id)
    return Response("ok", status=200)


def run_vk_webhook_server(host: str = "0.0.0.0", port: int = 8081, debug: bool = False) -> None:
    """Loads VK channels and starts Flask development server."""

    logging.basicConfig(level=logging.INFO, format="[%(levelname)s] %(message)s")
    registry.load_from_db()
    app.run(host=host, port=port, debug=debug)


if __name__ == "__main__":
    run_vk_webhook_server()

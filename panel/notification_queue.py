"""Background queue responsible for processing channel notifications."""

from __future__ import annotations

import logging
import threading
from queue import Empty, Queue

from .providers import ProviderError, ProviderFactory
from .repositories import (
    BotCredentialRepository,
    ChannelNotificationRepository,
    ChannelRepository,
)


log = logging.getLogger(__name__)


class NotificationQueue:
    def __init__(
        self,
        *,
        channel_repo: ChannelRepository,
        credential_repo: BotCredentialRepository,
        notification_repo: ChannelNotificationRepository,
        poll_interval: float = 5.0,
    ) -> None:
        self.channel_repo = channel_repo
        self.credential_repo = credential_repo
        self.notification_repo = notification_repo
        self.poll_interval = poll_interval
        self._queue: Queue[int] = Queue()
        self._stop = threading.Event()
        self._thread = threading.Thread(target=self._worker, name="channel-queue", daemon=True)
        self._provider_factory = ProviderFactory(credential_repo)
        self._thread.start()

    def stop(self) -> None:
        self._stop.set()
        self._queue.put_nowait(-1)
        self._thread.join(timeout=5)

    def enqueue(self, notification_id: int) -> None:
        log.debug("Notification %s enqueued", notification_id)
        self._queue.put(notification_id)

    def _worker(self) -> None:
        while not self._stop.is_set():
            try:
                notification_id = self._queue.get(timeout=self.poll_interval)
                if notification_id == -1:
                    continue
            except Empty:
                self._poll_pending()
                continue
            self._process(notification_id)

    def _poll_pending(self) -> None:
        for notification in self.notification_repo.dequeue_pending(limit=5):
            self.enqueue(notification.id)

    def _process(self, notification_id: int) -> None:
        notification = self.notification_repo.get(notification_id)
        if notification is None:
            log.warning("Notification %s not found", notification_id)
            return
        if notification.status not in {"pending", "retry", "in_progress"}:
            return
        log.debug("Processing notification %s", notification_id)
        self.notification_repo.mark_in_progress(notification_id)
        channel = self.channel_repo.get(notification.channel_id)
        if channel is None:
            log.error("Channel %s not found for notification %s", notification.channel_id, notification_id)
            self.notification_repo.mark_failed(notification_id, "Канал не найден")
            return
        try:
            provider = self._provider_factory.build(channel.platform, channel.credential_id, extra=channel.settings)
            message = str(notification.payload.get("message") or "")
            if not message:
                raise ProviderError("Пустое сообщение")
            recipient = notification.recipient or str(notification.payload.get("recipient") or "")
            if not recipient:
                raise ProviderError("Получатель не указан")
            provider.send(recipient, message, extra=channel.settings)
            self.notification_repo.mark_completed(notification_id)
        except ProviderError as exc:
            log.exception("Notification %s failed: %s", notification_id, exc)
            self.notification_repo.mark_failed(notification_id, str(exc))
        except Exception as exc:  # noqa: BLE001
            log.exception("Unexpected error while processing %s", notification_id)
            self.notification_repo.mark_failed(notification_id, f"Системная ошибка: {exc}")


_queue_instance: NotificationQueue | None = None


def init_queue() -> NotificationQueue:
    global _queue_instance
    if _queue_instance is None:
        channel_repo = ChannelRepository()
        credential_repo = BotCredentialRepository()
        notification_repo = ChannelNotificationRepository()
        _queue_instance = NotificationQueue(
            channel_repo=channel_repo,
            credential_repo=credential_repo,
            notification_repo=notification_repo,
        )
    return _queue_instance


def enqueue_notification(notification_id: int) -> None:
    queue = init_queue()
    queue.enqueue(notification_id)

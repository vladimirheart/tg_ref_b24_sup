# 2026-06-15 10:02:30 - notification read all boundary

## Промпты пользователя

- `давай дальше`

## Что изменено

- в `NotificationApiControllerWebMvcTest` добавлены отдельные проверки на `POST /api/notifications/read-all` для authenticated и anonymous identity resolution;
- в `NotificationApiIntegrationTest` добавлен live-сценарий `markAllAsReadTouchesOnlyCurrentIdentityAndReturnsUpdatedCount`, который закрепляет current-identity boundary и `updated` payload для mass-ack;
- в `DialogReadIntegrationTest` добавлен runtime-сценарий `notificationReadAllDoesNotHideUnreadDialogBeforeHistoryReread`, чтобы явно зафиксировать разделение consumer semantics: `read-all` гасит только bell unread, но не должен неявно менять `last_read_at`, dialog `unreadCount` и `my_dialogs.unanswered` до reread `history` consumer;
- в audit и roadmap добавлена отдельная фиксация, что bell boundary теперь закрыт не только для одиночного `/{id}/read`, но и для mass-ack `read-all`.

## Проверка

- `.\mvnw.cmd '-Dtest=NotificationApiControllerWebMvcTest,NotificationApiIntegrationTest,DialogReadIntegrationTest' test`

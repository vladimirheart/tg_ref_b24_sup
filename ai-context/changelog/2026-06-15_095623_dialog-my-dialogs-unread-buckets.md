# 2026-06-15 09:56:23 - dialog my dialogs unread buckets

## Промпты пользователя

- `продолжи`

## Что изменено

- в `spring-panel/src/main/java/com/example/panel/service/DialogLookupReadService.java` убран special-case, из-за которого `my_dialogs.unanswered` держал назначенный диалог в unanswered только потому, что у него `statusKey=waiting_operator`, даже если `unreadCount` уже был `0`;
- теперь bucket contract формулируется явно: assigned dialog попадает в `unanswered` только при `unreadCount > 0`, а при `unreadCount = 0` стабильно уходит в `in_work`, независимо от status overlay;
- в `DialogLookupReadServiceTest` добавлен отдельный unit-сценарий на `waiting_operator` с `unreadCount=0`, чтобы новая логика не вернулась скрыто через future refactor;
- integration-коридор расширен на read-side consumer surfaces: `DialogReadIntegrationTest`, `DialogWorkspaceIntegrationTest`, `DialogDetailsIntegrationTest` и `DialogQuickActionsIntegrationTest` теперь явно закрепляют переход assigned dialog из `unanswered` в `in_work` после reread при сохранении отдельной bell unread semantics;
- по пути подровнены нестабильные `statusKey` assertions в `DialogDetailsIntegrationTest`, где runtime может отдавать `waiting_operator` или `auto_processing` поверх того же reopen/rearm contract.

## Проверка

- `.\mvnw.cmd '-Dtest=DialogLookupReadServiceTest,DialogListIntegrationTest,DialogDetailsIntegrationTest,DialogReadIntegrationTest,DialogWorkspaceIntegrationTest,DialogQuickActionsIntegrationTest' test`

# 2026-07-14 09:15:00 — dialogs media/max + utf8 follow-up

## Промпт пользователя

> продолжи

## Что изменено

- Доведён до рабочего состояния `DialogReplyTransportService`: добавлены константы fallback для MAX API (`platform-api2.max.ru` и `platform-api.max.ru`) и ретраи отправки медиа при гонке готовности вложения.
- Подчищены оставшиеся пользовательские строки на странице диалогов и в runtime-модулях: убраны остатки битой кодировки, английские подписи `Compact mode` / `Legacy modal` / `Workspace` в видимых местах и приведены предупреждения к русскому интерфейсу.
- Обновлены интеграционные media-тесты под актуальное поведение с кэширующим `MultipartFile`, чтобы они проверяли реальный контракт, а не старый `MockMultipartFile`.
- Синхронизированы изменённые dialogs-ресурсы в `spring-panel/target/classes`, чтобы локальный рантайм не продолжал отдавать старые JS и шаблоны из собранных классов.

## Затронутые файлы

- `spring-panel/src/main/java/com/example/panel/service/DialogReplyTransportService.java`
- `spring-panel/src/main/resources/templates/dialogs/index.html`
- `spring-panel/src/main/resources/static/js/dialogs-actions-runtime.js`
- `spring-panel/src/main/resources/static/js/dialogs-experiment-runtime.js`
- `spring-panel/src/main/resources/static/js/dialogs-presentation-runtime.js`
- `spring-panel/src/main/resources/static/js/dialogs-shell-runtime.js`
- `spring-panel/src/main/resources/static/js/dialogs-workspace-runtime.js`
- `spring-panel/src/test/java/com/example/panel/controller/DialogQuickActionsIntegrationTest.java`

## Проверка

- `.\mvnw.cmd -q "-Dtest=DialogQuickActionServiceTest,DialogReplyServiceTest,DialogQuickActionsControllerWebMvcTest" test`
  - успешно
- `.\mvnw.cmd -q "-Dtest=DialogQuickActionsIntegrationTest#quickActionsApiMediaReplyRefreshesDetailsWorkspaceAndAuditTrail+quickActionsApiMediaReplyNotifiesPeerParticipantsThroughNotificationApi" test`
  - успешно

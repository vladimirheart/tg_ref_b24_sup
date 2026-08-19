# Changelog — 2026-08-19 10:03:56

## User prompt

`давай дальше крупным пакетом.`

## Context

После PostgreSQL-first bootstrap/default runtime и cleanup direct SQLite live-read next logical step по `01-183` был в том, чтобы снять ad-hoc SQLite schema ownership с live controller/service bean’ов `spring-panel`.

## What changed

- `spring-panel/src/main/resources/db/migration/sqlite/V41__create_ui_event_outbox.sql`
  - добавлена SQLite Flyway migration для `ui_event_outbox`.
- `spring-panel/src/main/java/com/example/panel/controller/PasswordResetRequestApiController.java`
  - удалён runtime `ensureRequestsTable()` и зависимость от `PanelDatabaseRuntimeMode`;
  - `password_reset_requests` теперь ожидается как schema owned by migrations.
- `spring-panel/src/main/java/com/example/panel/service/UiEventOutboxWatcher.java`
  - удалён constructor-time schema bootstrap;
  - watcher больше не создаёт `ui_event_outbox` по месту использования.
- `spring-panel/src/main/java/com/example/panel/service/ChatAttachmentMetadataService.java`
  - удалён constructor-time bootstrap `chat_attachment_metadata`.
- `spring-panel/src/main/java/com/example/panel/service/ChatAttachmentMetadataAvailabilityService.java`
  - удалён runtime `ALTER TABLE` / column bootstrap;
  - reconcile теперь только обновляет данные, но не владеет схемой.
- `spring-panel/src/test/java/com/example/panel/service/BotRuntimeTicketWriteServiceTest.java`
  - обновлён на новый `ChatAttachmentMetadataService` constructor;
  - заменена протухшая фиксированная `expiresAt` на `OffsetDateTime.now().plusDays(1)`.
- `spring-panel/src/test/java/com/example/panel/service/ChatAttachmentMetadataAvailabilityServiceTest.java`
  - обновлён под отсутствие schema mutation в live service.
- `docs/SQLITE_BOOTSTRAP_PERIMETER.md`, `docs/POSTGRESQL_FULL_PRODUCTION_GAP_AUDIT.md`, `ai-context/tasks/task-details/01-183.md`
  - зафиксировано, что ownership этих compatibility-таблиц возвращён в migration layer, а не в live-beans.

## Validation

- `spring-panel`: `./mvnw "-Dtest=ChatAttachmentMetadataAvailabilityServiceTest,BotRuntimeTicketWriteServiceTest" test`
  - результат: `BUILD SUCCESS`

## Additional notes

- Более широкий прогон с `SmokeTest` отдельно показал два несвязанных с этим пакетом хвоста:
  - `SmokeTest` падает на внешнем fixture-state (`users` count / monitoring credential decrypt), который использует текущие tracked SQLite-файлы окружения;
  - test run затронул tracked `settings.db` и `spring-panel/monitoring.db`.
- Эти `.db` изменения не являются целевой частью данного пакета и требуют отдельного решения, если их нужно очистить перед коммитом.

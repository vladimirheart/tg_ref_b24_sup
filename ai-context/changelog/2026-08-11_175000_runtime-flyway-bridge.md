# 2026-08-11 17:50:00 - runtime flyway bridge

## Request
давай следующий крупный шаг

## Summary
- добавлен PostgreSQL Flyway-срез `V7__runtime_auxiliary_tables.sql` для runtime-таблиц панели, которые раньше существовали только через SQLite self-bootstrap: `ticket_participants`, `ui_event_outbox`, `chat_attachment_metadata`, `password_reset_requests`;
- panel-сервисы `ChatAttachmentMetadataService`, `UiEventOutboxWatcher`, `DialogParticipantService` и `PasswordResetRequestApiController` переведены в `sqlite-only` bootstrap-режим, чтобы в external PostgreSQL-path ownership был у Flyway, а не у runtime DDL;
- `DialogParticipantService` избавлен от `INSERT OR IGNORE` и переведён на переносимую idempotent insert-логику;
- `ClientProfileApiController` переведён с `PRAGMA table_info(...)` на JDBC metadata;
- в `java-bot` добавлен JDBC metadata helper и переведены `SchemaDiagnosticsRunner` и `SqliteTriggerInitializer`, а `ChatAttachmentMetadataService` перестал пытаться создавать SQLite-специфичную схему в external DB-режиме.

## Files Changed
- `spring-panel/src/main/resources/db/migration/postgresql/V7__runtime_auxiliary_tables.sql`
- `spring-panel/src/main/java/com/example/panel/service/ChatAttachmentMetadataService.java`
- `spring-panel/src/main/java/com/example/panel/controller/PasswordResetRequestApiController.java`
- `spring-panel/src/main/java/com/example/panel/service/UiEventOutboxWatcher.java`
- `spring-panel/src/main/java/com/example/panel/service/DialogParticipantService.java`
- `spring-panel/src/main/java/com/example/panel/service/BotDatabaseRegistry.java`
- `spring-panel/src/main/java/com/example/panel/controller/ClientProfileApiController.java`
- `java-bot/bot-core/src/main/java/com/example/supportbot/support/JdbcSchemaInspector.java`
- `java-bot/bot-core/src/main/java/com/example/supportbot/diagnostics/SchemaDiagnosticsRunner.java`
- `java-bot/bot-core/src/main/java/com/example/supportbot/config/SqliteTriggerInitializer.java`
- `java-bot/bot-core/src/main/java/com/example/supportbot/service/ChatAttachmentMetadataService.java`

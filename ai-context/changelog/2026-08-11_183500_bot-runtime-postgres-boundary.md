# 2026-08-11 18:35:00 - bot runtime postgres boundary

## Request
- давай следующий крупный шаг

## Summary
- добавлен единый runtime-компонент `BotDatabaseRuntimeMode`, который определяет, работает ли `java-bot` в локальном SQLite-режиме или в external PostgreSQL-режиме;
- bot-side runtime bootstrap для `ticket_participants`, `ticket_attributes`, `ui_event_outbox` и `chat_attachment_metadata` ограничен SQLite-режимом, чтобы в external PostgreSQL эти таблицы считались обязанностью schema/migration-слоя, а не создавались сервисами на старте;
- `ChatAttachmentMetadataService` переведён с ad-hoc проверки env-переменных на общий runtime mode;
- в `ObjectPassportService` исправлена вставка `object_passports`: generated keys теперь запрашиваются явно через `Statement.RETURN_GENERATED_KEYS`.

## Files Changed
- `java-bot/bot-core/src/main/java/com/example/supportbot/config/BotDatabaseRuntimeMode.java`
- `java-bot/bot-core/src/main/java/com/example/supportbot/service/AutoCloseFollowUpTaskService.java`
- `java-bot/bot-core/src/main/java/com/example/supportbot/service/ChatAttachmentMetadataService.java`
- `java-bot/bot-core/src/main/java/com/example/supportbot/service/TicketAttributeService.java`
- `java-bot/bot-core/src/main/java/com/example/supportbot/service/UiEventOutboxService.java`
- `java-bot/bot-core/src/test/java/com/example/supportbot/config/BotDatabaseRuntimeModeTest.java`
- `spring-panel/src/main/java/com/example/panel/service/ObjectPassportService.java`

## Verification
- `cmd /c mvnw.cmd -q -pl bot-core test` (`java-bot`)
- `cmd /c mvnw.cmd -q compile` (`spring-panel`)

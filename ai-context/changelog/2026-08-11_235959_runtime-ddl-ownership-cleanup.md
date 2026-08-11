# 2026-08-11 23:59:59 - runtime ddl ownership cleanup

## Request
- `давай следующий действительно крупный шаг.`

## Summary
- из active external PostgreSQL path убраны незагейченные runtime DDL-изменения схемы;
- `java-bot/bot-core` больше не выполняет `ALTER TABLE chat_history ...` вне SQLite-режима;
- `spring-panel` больше не выполняет `ALTER TABLE chat_attachment_metadata ...` вне SQLite-режима в `ChatAttachmentMetadataAvailabilityService`;
- добавлена PostgreSQL Flyway-миграция `V15__chat_history_message_metadata_columns.sql` для колонок `original_message`, `forwarded_from`, `file_name`;
- добавлен `ChatHistoryServiceTest`, который подтверждает отсутствие schema mutation в external runtime пути;
- добавлен `ChatAttachmentMetadataAvailabilityServiceTest`, но его execution пока блокируется уже существующей ошибкой компиляции в `BotProcessServiceTest`.

## Files Changed
- `java-bot/bot-core/src/main/java/com/example/supportbot/service/ChatHistoryService.java`
- `java-bot/bot-core/src/test/java/com/example/supportbot/service/ChatHistoryServiceTest.java`
- `spring-panel/src/main/java/com/example/panel/service/ChatAttachmentMetadataAvailabilityService.java`
- `spring-panel/src/main/resources/db/migration/postgresql/V15__chat_history_message_metadata_columns.sql`
- `spring-panel/src/test/java/com/example/panel/service/ChatAttachmentMetadataAvailabilityServiceTest.java`
- `ai-context/tasks/task-details/01-181.md`

## Verification
- `cmd /c mvnw.cmd -q -pl bot-core -Dtest=ChatHistoryServiceTest test` (`java-bot`) - passed
- `cmd /c mvnw.cmd -q -DskipTests compile` (`spring-panel`) - passed
- `cmd /c mvnw.cmd -q -Dtest=ChatAttachmentMetadataAvailabilityServiceTest test` (`spring-panel`) - blocked by existing unrelated compile error in `spring-panel/src/test/java/com/example/panel/service/BotProcessServiceTest.java:364`
- `git diff --check` - only CRLF/LF warnings, no diff formatting errors

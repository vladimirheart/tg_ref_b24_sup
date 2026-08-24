# 2026-08-24 14:45 — AI memory exact text + PostgreSQL reply/audit runtime v46

## User report

После восстановления ticket ingestion новое обращение и AI suggestion появились. При этом memory solution показывался с лишней generated-обвязкой; после редактирования и отправки операторского reply клиент получал сообщение, но UI возвращал внутреннюю ошибку; в runtime повторялся WARN об отсутствующей `dialog_action_audit`; при штатной остановке Maven печатал `BUILD FAILURE`.

## Evidence

- `DialogAiAssistantSuggestionService` оборачивал memory через deterministic/composer flow и добавлял source-prefix;
- `AiRetrievalService` предпочитал `knowledgeBody` перед `solutionText` для memory candidate;
- `DialogReplyTargetService` передавал `OffsetDateTime.now().toString()` как VARCHAR в PostgreSQL `TIMESTAMPTZ`;
- `DialogAuditService` использует `dialog_action_audit`, но PostgreSQL migrations до V33 её не создавали;
- Maven stop-noise относится к launcher lifecycle и вынесен отдельно в 01-187, чтобы не маскировать реальные crashes.

## Changes

- solution memory получила exact-text runtime contract для suggestion и auto-reply;
- derived knowledge оставлено только evidence/metadata для memory candidate;
- temporal JDBC bindings переведены на `OffsetDateTime` objects;
- добавлена PostgreSQL V34 `dialog_action_audit`;
- добавлены regression tests для memory exact-text и timestamp binding;
- добавлена 01-186 и отдельная 01-187 для clean Windows shutdown semantics;
- обновлены live-smoke notes 01-184/01-185.

## Files / areas

- `spring-panel/src/main/java/com/example/panel/service/AiRetrievalService.java`
- `spring-panel/src/main/java/com/example/panel/service/DialogAiAssistantSuggestionService.java`
- `spring-panel/src/main/java/com/example/panel/service/DialogReplyTargetService.java`
- `spring-panel/src/main/resources/db/migration/postgresql/V34__dialog_action_audit.sql`
- AI/reply regression tests
- `ai-context/tasks/`

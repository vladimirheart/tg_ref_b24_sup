# 2026-08-11 23:59:30 - runtime sql hot path portability

## Request
- `давай следующий действительно крупный шаг.`
- `делаем так, как быстрее, потому что проект пока не запущен в бой`

## Summary
- в `spring-panel` добавлен общий helper `PanelTimestampSqlSupport`, который централизует portable timestamp SQL для SQLite и external PostgreSQL;
- на helper переведён AI/runtime hot path чтения: `AiMonitoringService`, `DialogAiAssistantConfigService`, `DialogClientContextReadService`, `AiOpsRuntimeService` и `AiRetrievalService`;
- из этого hot path убраны SQLite-only `datetime('now', ?)`, `datetime(substr(...))` и `ORDER BY substr(...timestamp...)`, мешавшие fresh PostgreSQL-first runtime path;
- обновлены прямые тестовые конструкторы сервисов и добавлен unit-test `PanelTimestampSqlSupportTest`;
- в задаче `01-181` зафиксирован ускоренный маршрут: без отдельной `SQLite -> PostgreSQL` migration utility в ближайшем practical scope, с фокусом на fresh PostgreSQL deployment path.

## Files Changed
- `spring-panel/src/main/java/com/example/panel/support/PanelTimestampSqlSupport.java`
- `spring-panel/src/main/java/com/example/panel/service/AiMonitoringService.java`
- `spring-panel/src/main/java/com/example/panel/service/DialogAiAssistantConfigService.java`
- `spring-panel/src/main/java/com/example/panel/service/DialogClientContextReadService.java`
- `spring-panel/src/main/java/com/example/panel/service/AiOpsRuntimeService.java`
- `spring-panel/src/main/java/com/example/panel/service/AiRetrievalService.java`
- `spring-panel/src/test/java/com/example/panel/support/PanelTimestampSqlSupportTest.java`
- `spring-panel/src/test/java/com/example/panel/service/DialogAiAssistantConfigServiceTest.java`
- `spring-panel/src/test/java/com/example/panel/service/DialogClientContextReadServiceTest.java`
- `spring-panel/src/test/java/com/example/panel/service/AiRetrievalServiceTest.java`
- `spring-panel/src/test/java/com/example/panel/service/AiOfflineEvaluationServiceTest.java`
- `ai-context/tasks/task-details/01-181.md`

## Verification
- `cmd /c mvnw.cmd -q -DskipTests compile` (`spring-panel`) - passed
- `git diff --check` - only CRLF/LF warnings, no diff formatting errors
- targeted `spring-panel` tests still cannot be executed via Maven because repository has a pre-existing unrelated `testCompile` blocker in `src/test/java/com/example/panel/service/BotProcessServiceTest.java:364`

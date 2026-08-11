# 2026-08-11 23:59:50 - dialog read postgres portability

## Request
- `давай следующий действительно крупный шаг.`

## Summary
- `DialogLookupReadService` переведён на portable timestamp/order/aggregation path через `PanelTimestampSqlSupport`;
- из dialog lookup SQL убраны PostgreSQL-blockers `GROUP_CONCAT(...)`, `ORDER BY substr(...)`, fallback `substr(...created_at...)` в active query path и небезопасный `COALESCE(..., '')` для timestamp comparison в unread-count;
- `DialogConversationReadService` больше не зависит от SQLite-only `rowid` и `substr(ch.timestamp, ...)` в history/previous-history ordering;
- обновлены direct constructor tests для dialog read сервисов;
- карточка `01-181` обновлена новым practical остатком: после AI/runtime и dialog hot paths остаётся уже более точечный portability cleanup, а не большой schema/runtime gap.

## Files Changed
- `spring-panel/src/main/java/com/example/panel/service/DialogLookupReadService.java`
- `spring-panel/src/main/java/com/example/panel/service/DialogConversationReadService.java`
- `spring-panel/src/main/java/com/example/panel/support/PanelTimestampSqlSupport.java`
- `spring-panel/src/test/java/com/example/panel/service/DialogLookupReadServiceTest.java`
- `spring-panel/src/test/java/com/example/panel/service/DialogConversationReadServiceTest.java`
- `ai-context/tasks/task-details/01-181.md`

## Verification
- `cmd /c mvnw.cmd -q -DskipTests compile` (`spring-panel`) - passed
- `git diff --check` - only CRLF/LF warnings, no diff formatting errors

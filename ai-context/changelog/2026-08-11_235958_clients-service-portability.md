# 2026-08-11 23:59:58 - clients service portability

## Request
- `давай следующий действительно крупный шаг.`

## Summary
- `ClientsService` очищен от SQLite-only date/time SQL в active client analytics/profile path;
- `loadClientProfile(...)` больше не использует SQLite `datetime(...)` для `resolved_at`;
- `loadTotalMinutes(...)` больше не зависит от `julianday(...)` и `replace(substr(...))`: расчёт support-time перенесён в Java по уже выбранным timestamps;
- добавлен `ClientsServiceTest` с PostgreSQL-compatible in-memory datasource на client list/profile path;
- карточка `01-181` обновлена новым фактическим остатком: после этого шага активный `spring-panel` PostgreSQL path почти дочищен, а явные SQLite-only следы остаются в основном только в уже ожидаемом `sqlite-only` perimeter.

## Files Changed
- `spring-panel/src/main/java/com/example/panel/service/ClientsService.java`
- `spring-panel/src/test/java/com/example/panel/service/ClientsServiceTest.java`
- `ai-context/tasks/task-details/01-181.md`

## Verification
- `cmd /c mvnw.cmd -q -DskipTests compile` (`spring-panel`) - passed
- `git diff --check` - only CRLF/LF warnings, no diff formatting errors
- remaining explicit SQLite-only hits in active search narrowed to `BotDatabaseRegistry` and SQLite-specific fallback branches in `DialogLookupReadService`

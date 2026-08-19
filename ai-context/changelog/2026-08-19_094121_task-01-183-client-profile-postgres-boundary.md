# Changelog — 2026-08-19 09:41:21

## User prompt

`проверь текущее состояние и выполни следующий логичный шаг`

## Context

После предыдущих срезов `01-183` bootstrap и default runtime уже были переведены в PostgreSQL-first. Следующим логичным шагом стало убрать operator-facing live-path, который в external runtime всё ещё мог напрямую читать per-channel SQLite-файл `bot-<channelId>.db`.

## What changed

- `spring-panel/src/main/java/com/example/panel/controller/ClientProfileApiController.java`
  - добавлен `PanelDatabaseRuntimeMode`;
  - compatibility probe в `bot-<channelId>.db` теперь разрешён только в явном `sqlite` runtime;
  - в external PostgreSQL runtime контроллер больше не открывает per-channel SQLite-файл как implicit live source;
  - прямой `SQLiteDataSource` заменён на `SqliteConnectionConfigSupport`.
- `spring-panel/src/test/java/com/example/panel/controller/ClientProfileApiControllerTest.java`
  - добавлены unit-тесты, что:
    - registry не используется вне `sqlite` mode;
    - existing SQLite path используется только в `sqlite` mode;
    - missing SQLite path корректно даёт empty result.
- `docs/SQLITE_BOOTSTRAP_PERIMETER.md`
  - зафиксировано, что operator-facing live reads не должны напрямую открывать per-channel SQLite в `APP_DB_MODE=postgresql`.
- `docs/POSTGRESQL_FULL_PRODUCTION_GAP_AUDIT.md`
  - добавлена пометка, что direct SQLite probes в operator-facing path уже начали вычищаться.
- `ai-context/tasks/task-details/01-183.md`
  - добавлен новый update по этому production-slice.

## Validation

- `spring-panel`: `./mvnw "-Dtest=ClientProfileApiControllerTest,BotDatabaseRegistryTest" test`
  - результат: `BUILD SUCCESS`

## Notes

- На момент завершения среза рабочее дерево было чистым до внесения этих изменений.
- Это не закрывает `01-183`, но убирает ещё один скрытый SQLite fallback из live operator path при external PostgreSQL runtime.

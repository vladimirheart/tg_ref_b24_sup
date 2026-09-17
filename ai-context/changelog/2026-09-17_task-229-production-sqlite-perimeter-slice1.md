# 2026-09-17 — задача 01-229: retire selectable panel SQLite compatibility (slice 1)

## Контекст

После принятого PostgreSQL cutover `01-228` начат structural cleanup `01-229`. Этот slice не удаляет archive/import evidence и не затрагивает bot `worker` technical store.

## Изменения

- `spring-panel DatabaseMode.from("sqlite")` теперь fail-fast отклоняет retired panel SQLite runtime mode.
- Удалён `EnvDefaultsInitializer`, его unit test и обе startup registrations.
- Добавлен production contract test для canonical PostgreSQL mode и retired SQLite selector.
- `ai-context/rules/backend/04-sqlite-topology.md` переопределён как archive/test perimeter после cutover.
- `docs/SQLITE_BOOTSTRAP_PERIMETER.md`, `docs/configuration.md` и `docs/environment_variables.md` синхронизированы с новым boundary.
- Task metadata фиксирует slice 1 без преждевременного перевода `01-229` в `🟣`.

## Сохранено намеренно

- `docker-compose.legacy-sqlite-import.yml`;
- staging/verification scripts;
- backend-owned one-time import/recovery services и ledger;
- legacy SQLite sources/archive evidence;
- bot `APP_DB_MODE=worker` temporary technical store.

## Safety

Этот structural step не выполняет deployment, runtime restart, DB migration, backfill, legacy import или external-system mutation.

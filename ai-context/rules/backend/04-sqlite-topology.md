# Правило: SQLite archive/test perimeter после production cutover

## Статус

Действует после принятой задачи `01-228`.

## Production source of truth

- `spring-panel` production runtime использует PostgreSQL как единственный canonical business/runtime storage.
- `APP_DB_MODE=sqlite` для `spring-panel` не является поддерживаемым runtime mode.
- Normal production compose не должен монтировать legacy SQLite sources в `panel-web`, `ops-worker` или `bot-runner`.
- Legacy SQLite import/recovery не должен запускаться автоматически в обычном PostgreSQL runtime.

## Где SQLite ещё допустим

1. **Архивный migration/recovery tool**
   - read-only staged legacy sources;
   - `docker-compose.legacy-sqlite-import.yml`;
   - `scripts/stage-legacy-sqlite-import.*`;
   - `scripts/verify-legacy-sqlite-import.ps1`;
   - backend-owned import/recovery ledger и explicit one-time guard.
2. **Тестовые fixtures** — временные SQLite-файлы допускаются только как test implementation detail.
3. **Изолированный bot worker technical store** — `APP_DB_MODE=worker` может использовать временный SQLite-файл только для self-owned coordination/dedup state. Он не владеет canonical business schema и не является SQLite compatibility mode.
4. **Legacy source/archive** — старые `*.db` могут сохраняться для rollback/evidence до отдельного принятого purge-шагa, но не считаются live runtime storage.

## Что запрещено в production

- `spring-panel` startup через `APP_DB_MODE=sqlite`;
- скрытая подстановка `APP_DB_*` путей как live datasource defaults;
- local SQLite business writes из panel/bot runtime;
- создание secondary business SQLite databases при PostgreSQL startup;
- silent fallback с PostgreSQL/RabbitMQ/internal API обратно в local business SQLite;
- implicit import/recovery из legacy `*.db` без explicit archive operator.

## Изменения этого правила

Если задача меняет archive/import boundary, worker technical-store contract или окончательный purge legacy sources, она должна обновить это правило и соответствующий runbook в том же structural scope.

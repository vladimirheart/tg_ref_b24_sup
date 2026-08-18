# 2026-08-18 14:21:41 - task 01-183 postgres-first bootstrap hardening

## Пользовательский промпт

`выполни шаги по задаче 01-183`

## Что изменено

- `scripts/bootstrap-first-run.ps1` и `scripts/bootstrap-first-run.sh` переведены на production-oriented first-run semantics:
  - default `auto`-bootstrap больше не откатывается в SQLite при недоступном Docker;
  - SQLite оставлен только как explicit compatibility override или аварийный fallback при отдельном явном флаге;
- `.env.example` обновлён под PostgreSQL-first bootstrap по умолчанию;
- обновлены ключевые документы (`README.md`, `docs/configuration.md`, `docs/environment_variables.md`, `docs/SQLITE_BOOTSTRAP_PERIMETER.md`, `docs/target-production-architecture-plan.md`, `docs/POSTGRESQL_FULL_PRODUCTION_GAP_AUDIT.md`) под новое фактическое поведение bootstrap;
- в `ai-context/tasks/task-details/01-183.md` добавлен progress-update по первому practical production-slice.

## Проверка

- `powershell -NoLogo -NoProfile -ExecutionPolicy Bypass -File scripts/bootstrap-first-run.ps1 -ValidateOnly` с `IGUANA_BOOTSTRAP_DB_MODE=auto`
  - ожидаемо завершился ошибкой при недоступном Docker;
- `powershell -NoLogo -NoProfile -ExecutionPolicy Bypass -File scripts/bootstrap-first-run.ps1 -ValidateOnly` с `IGUANA_BOOTSTRAP_DB_MODE=sqlite`
  - успешно прошёл;
- `bash ./scripts/bootstrap-first-run.sh --validate-only` с `IGUANA_BOOTSTRAP_DB_MODE=sqlite`
  - успешно прошёл через Git Bash.

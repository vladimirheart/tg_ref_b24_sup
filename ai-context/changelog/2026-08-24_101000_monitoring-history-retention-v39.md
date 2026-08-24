# 2026-08-24 10:10 — Monitoring history retention v39

## User prompt

`репо пушнул - проверяй и давай дальше`

## Scope

Закрыть `01-140` после NetBox closeout, не регрессируя собранный PostgreSQL-first production contour.

## Changes

- `monitoring_check_history` получил обязательный 30-дневный retention на canonical runtime;
- startup + periodic cleanup работает через `MonitoringCheckHistoryRetentionService`; в external mode используется Redis lease;
- PostgreSQL получил `V30` индекс по `created_at`, SQLite monitoring bootstrap создаёт эквивалентный индекс;
- SQLite compatibility bootstrap переносит свежую legacy history в `monitoring.db`, удаляет старую source-копию после успешного прохода и best-effort выполняет `VACUUM`;
- PostgreSQL-first получил opt-in verified compactor для старых `panel_runtime.db`, `monitoring.db`, `bot_runtime.db`, `bot_database.db`;
- compactor докопирует отсутствующие свежие строки по content fingerprint и fail-closed откажется удалять source при неразбираемых current-window данных;
- destructive compaction включается только совместно `IGUANA_LEGACY_SQLITE_AUTO_IMPORT=true` + `IGUANA_LEGACY_MONITORING_HISTORY_COMPACT=true`;
- добавлены SQLite/H2 regression tests migration, retention и compaction paths;
- `01-140` переведена в `🟣` и ждёт ручного smoke/size verification.

## Architecture note

После `01-181..01-183` production history остаётся в canonical PostgreSQL. Формулировка старой задачи про обязательный `monitoring.db` применяется только к SQLite compatibility; возвращать production monitoring state в локальный SQLite было бы архитектурной регрессией.
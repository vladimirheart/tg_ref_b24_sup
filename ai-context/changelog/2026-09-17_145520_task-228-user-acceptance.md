# 2026-09-17 14:55:20 +03:00 — задача 01-228: пользовательская приёмка PostgreSQL cutover

## Пользовательский запрос

`01-228 принимаю`

Перед явной приёмкой пользователь выполнил read-only verification и получил:

`[GREEN] Critical legacy SQLite table counts are covered by PostgreSQL and recovery evidence exists.`

## Фактическая проверка

- `IGUANA_LEGACY_SQLITE_AUTO_IMPORT=false` в локальном `.env`.
- Production Java roles работают в `APP_DB_MODE=postgresql` с PostgreSQL JDBC URL.
- Legacy SQLite mount отсутствует у `panel-web`, `ops-worker`, `bot-runner`.
- Свежие logs не содержат SQLite/import/recovery runtime signs.
- Critical coverage: messages `21/45`, chat_history `250/603`, notifications `689/5094`, web_form_sessions `1/1`, chat_attachment_metadata `17/107` (SQLite/PostgreSQL).
- Recovery ledger rows: `6`.
- Changed bot shard markers: `0`.

## Изменения metadata

- Зафиксирована явная ручная acceptance `01-228`.
- Статус `01-228` изменён с `🟣` на `🟢`.
- `01-229` не запускается этим шагом; acceptance только снимает её dependency gate.

## Затронутые файлы

- `ai-context/tasks/task-list.md`
- `ai-context/tasks/task-details/01-228.md`
- этот changelog

## Runtime / data safety

Этот metadata-only acceptance step не запускает Docker, deployment, DB migration, backfill, legacy import или NetBox/external-system mutation и не меняет `.env`.

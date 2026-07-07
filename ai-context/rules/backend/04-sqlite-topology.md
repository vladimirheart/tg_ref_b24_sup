# Правило: каноническая топология SQLite-баз

## Статус

Действует.

## Цель

Зафиксировать target-state и transitional-правила для SQLite-файлов проекта, чтобы команда не смешивала:

- логические bounded-контексты;
- текущие physical filenames legacy-эпохи;
- временные bootstrap/registry-файлы;
- technical history и business source of truth.

## Target-state

Target-state topology проекта описана в `docs/db/sqlite-target-topology.md`.

По умолчанию проект должен двигаться к `5` logical contours:

- `panel-runtime`
- `panel-identity`
- `monitoring`
- `panel-telemetry`
- `bot-runtime`

Исключения допускаются только по отдельной архитектурной задаче с явным обоснованием по росту, retention или operational isolation.

## Transitional ownership до полной миграции

Пока physical rename и переносы не завершены, источником истины для runtime ownership остаётся следующее сопоставление.

### `APP_DB_TICKETS` / `tickets.db`

Текущий physical файл `tickets.db` уже трактуется как logical contour `panel-runtime`.

Здесь допустимо канонически держать:

- `tickets`, `messages`, `chat_history`
- `ticket_*`, `task_*`, `tasks`
- `notifications`, `channels`, `settings_parameters`
- `client_*`
- `knowledge_*`, `it_equipment_catalog`
- `objects`, `object_passports`
- AI state/knowledge таблицы panel-flow

Новые raw telemetry/audit таблицы не должны автоматически селиться здесь, если их lifecycle явно технический и ограниченный по времени.

### `APP_DB_USERS` / `users.db`

Текущий physical файл `users.db` трактуется как logical contour `panel-identity`.

Здесь канонически живут:

- `users` / `panel_users`
- `roles`
- `user_authorities`
- password reset и session-related таблицы

### `APP_DB_MONITORING` / `monitoring.db`

Текущий physical файл `monitoring.db` трактуется как logical contour `monitoring`.

Здесь канонически живут:

- SSL/RMS/iiko monitoring tables
- `monitoring_check_history`
- monitoring rollups
- связанные очереди, state и retention-данные мониторинга

### `panel_telemetry.db` (будущий отдельный контур)

До появления отдельного physical файла новые задачи должны рассматривать этот logical contour как целевое место для:

- `dialog_action_audit`
- `workspace_telemetry_audit`
- `ai_agent_event_log`
- прочей технической telemetry/history, не являющейся business source of truth

Если код пока физически пишет такие таблицы в `tickets.db`, новая задача обязана либо оформить migration path в `panel_telemetry`, либо явно зафиксировать, почему это временное исключение.

### `APP_DB_BOT` / `bot_database.db`

Текущий physical файл `bot_database.db` трактуется как logical contour `bot-runtime`.

Здесь канонически живут:

- `bot_users`
- `bot_chat_history`
- `applications`
- связанные bot-side transport/runtime записи

Per-channel `bot-<channelId>.db` не должны считаться отдельными доменными базами. Это либо temporary runtime-artifact, либо shard-слой того же `bot-runtime`.

## Transitional legacy, который не должен становиться target-state

### `APP_DB_SETTINGS` / `settings.db`

Допустим только как временный registry/bootstrap-файл.

Разрешённый scope:

- `database_registry`
- `database_links`
- `bot_instances`

Новые runtime-домены нельзя закреплять за `settings.db`.

### `clients.db`, `knowledge_base.db`, `objects.db`

Эти secondary-файлы допустимы как transitional artifacts, но не как долгосрочные canonical DBs по умолчанию.

Новая runtime-логика не должна:

- считать их главным source of truth только по факту существования файла;
- добавлять туда новые домены без отдельного архитектурного решения;
- усиливать split ради split.

## Практические правила

- Архитектурные решения нужно принимать по logical contour, а не по legacy filename.
- Новые индексы для диалогов сначала рассматриваются для `panel-runtime.chat_history`; до physical rename это означает `tickets.db.chat_history`.
- Secondary SQLite datasource должны собираться через общий конфигурационный контракт (`journal_mode`, `busy_timeout`, transaction mode, date format), а не через разрозненные ad hoc подключения.
- Technical history по умолчанию проектируется либо в `monitoring`, либо в будущий `panel-telemetry`, а не в `panel-runtime`.
- Если новая задача реально переносит домен в другой logical contour или создаёт новый physical файл, она обязана обновить:
  - runtime wiring;
  - bootstrap/migration слой;
  - `docs/database-paths.md`;
  - `docs/db/sqlite-target-topology.md`, если меняется target-state;
  - это правило, если меняется ownership-модель.

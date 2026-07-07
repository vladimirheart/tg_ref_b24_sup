# Правило: каноническая топология SQLite-баз

## Статус

Действует.

## Цель

Зафиксировать реальный source of truth для SQLite-файлов проекта и не смешивать:

- рабочие канонические базы;
- legacy-совместимые таблицы в `tickets.db`;
- bootstrap/registry-базы, которые ещё не стали полноправными runtime-контекстами.

## Канонические базы

### `tickets.db`

Основная operational-база панели.

Здесь канонически живут:

- `tickets`
- `messages`
- `chat_history`
- `ticket_*`
- `tasks`, `task_*`
- `notifications`
- `settings_parameters`
- `knowledge_articles`, `knowledge_article_files`
- `client_statuses`, `client_usernames`, `client_phones`, `client_blacklist`, `client_unblock_requests`, `client_avatar_history`
- `channels`
- AI/runtime audit таблицы панели

`tickets.db` остаётся главным JPA/Flyway datasource панели.

### `users.db`

Отдельная каноническая база пользователей панели и доступа:

- `users`
- `user_authorities`
- `roles`
- служебные таблицы, относящиеся только к panel-auth

### `monitoring.db`

Каноническая база мониторингового контура:

- SSL/RMS/iiko monitoring tables
- `monitoring_check_history`
- связанные очереди, state и retention-данные мониторинга

### `objects.db`

Каноническая база паспортов объектов:

- `objects`
- `object_passports`

### `bot_database.db`

Каноническая shared bot-runtime база legacy-контура, пока часть panel-flow всё ещё читает именно её:

- `bot_users`
- `bot_chat_history`
- `applications`
- связанные bot-side operational записи

## Bootstrap и registry-контур

### `settings.db`

Пока используется как registry/bootstrap-база split-контура, а не как главный settings-runtime.

Канонически хранит:

- `database_registry`
- `database_links`
- `bot_instances`

### `clients.db`, `knowledge_base.db`

Сейчас это подготовленные secondary-базы для будущего bounded split. Их bootstrap должен быть консистентным, но новая runtime-логика не должна молча считать их главным source of truth без отдельного архитектурного переноса.

### `APP_BOT_DATABASE_DIR` / `bot-<channelId>.db`

Per-channel bot DBs допустимы как runtime-артефакт канала и должны создаваться через общий SQLite datasource-контур. Но до отдельной миграции panel-read path нельзя считать их единственной канонической заменой `bot_database.db`.

## Практические правила

- Новые индексы для диалогов сначала рассматриваются для `tickets.db.chat_history`, потому что именно он сейчас обслуживает hot-path списка диалогов, истории и watcher-проверок.
- Secondary SQLite datasource должны собираться через общий конфигурационный контракт (`journal_mode`, `busy_timeout`, transaction mode, date format), а не через разрозненные ad hoc подключения.
- Если новая задача реально переносит домен из `tickets.db` в отдельный файл, она обязана обновить:
  - runtime wiring;
  - bootstrap/migration слой;
  - `docs/database-paths.md`;
  - это правило, если меняется каноническая ownership-модель.

📁 Пути к базам данных

В проекте уже есть split-контур SQLite, но не все файлы одинаковы по роли. Ниже зафиксирована реальная operational ownership-модель, а не только желаемая схема.

## Канонические operational-базы

- `APP_DB_TICKETS` — основной runtime панели: заявки, диалоги, уведомления, настройки панели, knowledge/client runtime-таблицы и audit-контур.
- `APP_DB_USERS` — пользователи панели, роли и доступ.
- `APP_DB_MONITORING` — monitoring runtime и история проверок.
- `APP_DB_OBJECTS` — паспорта объектов.
- `APP_DB_BOT` — shared bot-runtime база legacy-контура, которую часть panel-flow всё ещё использует напрямую.

## Registry / bootstrap-базы

- `APP_DB_SETTINGS` — registry split-контура: `database_registry`, `database_links`, `bot_instances`.
- `APP_DB_CLIENTS` — подготовленный secondary-файл клиентского домена; пока не является главным runtime source of truth панели.
- `APP_DB_KNOWLEDGE` — подготовленный secondary-файл базы знаний; пока не является главным runtime source of truth панели.
- `APP_BOT_DATABASE_DIR` — каталог с per-channel базами `bot-<channelId>.db`; используется для channel-local bootstrap/runtime, но не заменяет автоматически shared `APP_DB_BOT` для panel-read path.

## Важное ограничение

`tickets.db` по историческим причинам всё ещё содержит legacy-совместимые таблицы из mono-db раскладки. Для новых задач это не означает, что любой домен нужно продолжать развивать в `tickets.db`: source of truth определяется runtime ownership и правилами из `ai-context/rules/backend/04-sqlite-topology.md`.

## Пример

```bash
export APP_DB_TICKETS="/srv/iguana/tickets.db"
export APP_DB_USERS="/srv/iguana/users.db"
export APP_DB_MONITORING="/srv/iguana/monitoring.db"
export APP_DB_CLIENTS="/srv/iguana/clients.db"
export APP_DB_KNOWLEDGE="/srv/iguana/knowledge_base.db"
export APP_DB_OBJECTS="/srv/iguana/objects.db"
export APP_DB_SETTINGS="/srv/iguana/settings.db"
export APP_DB_BOT="/srv/iguana/bot_database.db"
export APP_BOT_DATABASE_DIR="/srv/iguana/bots"
```

Панель автоматически зарегистрирует пути в таблице `database_registry` внутри `settings.db`, чтобы фиксировать связи между базами.

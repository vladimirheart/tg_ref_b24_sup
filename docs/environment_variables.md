# 🌍 Переменные окружения

Ниже перечислены основные переменные, используемые Java-панелью и Java-ботом.

## Базовые переменные

| Переменная | Описание | Где используется |
| --- | --- | --- |
| `TELEGRAM_BOT_TOKEN` | токен Telegram-бота | Java-бот |
| `TELEGRAM_BOT_USERNAME` | @username бота | Java-бот |
| `GROUP_CHAT_ID` | ID рабочей группы/чата | Java-бот |
| `VK_BOT_ENABLED` | включить VK-бота (`true/false`) | Java-бот |
| `VK_BOT_TOKEN` | токен VK | Java-бот |
| `VK_GROUP_ID` | ID сообщества VK | Java-бот |
| `VK_OPERATOR_CHAT_ID` | чат операторов VK | Java-бот |
| `MAX_BOT_ENABLED` | включить MAX-бота (`true/false`) | Java-бот |
| `MAX_BOT_TOKEN` | токен MAX | Java-бот |
| `MAX_SUPPORT_CHAT_ID` | чат операторов MAX | Java-бот |
| `APP_DB_MODE` | режим БД: normal runtime default `postgresql`; `sqlite` только явный compatibility override; для панели ещё `mysql`, `auto` допустим только как ручной transitional режим | Панель и бот |
| `DATABASE_URL` | compatibility shorthand для external DB; для `java-bot` поддержан только PostgreSQL | Панель и бот |
| `SPRING_DATASOURCE_URL` | явный JDBC URL для external DB | Панель и бот |
| `SPRING_DATASOURCE_USERNAME` | пользователь external DB | Панель и бот |
| `SPRING_DATASOURCE_PASSWORD` | пароль external DB | Панель и бот |
| `IGUANA_BOOTSTRAP_DB_MODE` | режим first-run bootstrap: `auto`, `sqlite`, `postgresql`; normal path должен вести в PostgreSQL/RabbitMQ | bootstrap scripts |
| `APP_POSTGRES_PORT` | локальный порт для dockerized PostgreSQL bootstrap | bootstrap scripts |
| `IGUANA_BOOTSTRAP_INSTALL_DOCKER` | разрешить Windows bootstrap автоматически поставить Docker Desktop через `winget` | bootstrap scripts |
| `IGUANA_BOOTSTRAP_ALLOW_SQLITE_FALLBACK` | аварийно разрешить `auto`-bootstrap откатиться на SQLite, если Docker не стал доступен | bootstrap scripts |
| `IGUANA_BOOTSTRAP_DOCKER_READY_TIMEOUT_SECONDS` | timeout ожидания готовности Docker Desktop после установки/старта | bootstrap scripts |

## Базы данных

| Переменная | Описание | По умолчанию |
| --- | --- | --- |
| `APP_DB_PANEL_RUNTIME` | каноническая operational-база панели | `panel_runtime.db` |
| `APP_DB_PANEL_IDENTITY` | база пользователей панели | `panel_identity.db` |
| `APP_DB_BOT_RUNTIME` | shared bot runtime база | `bot_runtime.db` |
| `APP_DB_TICKETS` | legacy alias для `APP_DB_PANEL_RUNTIME` | `panel_runtime.db` |
| `APP_DB_USERS` | legacy alias для `APP_DB_PANEL_IDENTITY` | `panel_identity.db` |
| `APP_DB_BOT` | legacy alias для `APP_DB_BOT_RUNTIME` | `bot_runtime.db` |
| `SUPPORT_BOT_DATABASE_PATH` | явный shared SQLite bridge для `java-bot` compatibility mode | unset |
| `APP_DB_CLIENTS` | база клиентов | `clients.db` |
| `APP_DB_KNOWLEDGE` | база знаний | `knowledge_base.db` |
| `APP_DB_OBJECTS` | база объектов | `objects.db` |
| `APP_DB_SETTINGS` | общая база настроек | `settings.db` |
| `APP_BOT_DATABASE_DIR` | каталог баз каждого бота | `../bot_databases` |
| `APP_BOT_SQLITE_PER_CHANNEL_SHARD_ENABLED` | разрешить legacy bootstrap `bot-<channelId>.db` shard-layer даже в SQLite mode | `false` |

## Хранилища

| Переменная | Описание | По умолчанию |
| --- | --- | --- |
| `APP_STORAGE_ATTACHMENTS` | вложения | `../attachments` |
| `APP_STORAGE_KNOWLEDGE_BASE` | файлы базы знаний | `../attachments/knowledge_base` |
| `APP_STORAGE_AVATARS` | аватары | `../attachments/avatars` |
| `APP_STORAGE_WEBFORMS` | формы | `../attachments/forms` |
| `APP_ADMIN_PYTHON_EXECUTABLE` | python executable для admin storage inventory | `python` |
| `APP_ADMIN_REPOSITORY_ROOT` | явный repo root для admin storage inventory | auto-detect |
| `APP_ADMIN_STORAGE_INVENTORY_TIMEOUT` | timeout запуска inventory из админки | `90s` |

## Пример запуска

```bash
export TELEGRAM_BOT_TOKEN="123:ABC"
export APP_DB_MODE="postgresql"
export SPRING_DATASOURCE_URL="jdbc:postgresql://db.example.local:5432/iguana"
export SPRING_DATASOURCE_USERNAME="iguana"
export SPRING_DATASOURCE_PASSWORD="secret"
```

Для external PostgreSQL-режима рекомендуется явно фиксировать режим и стандартные Spring datasource-поля:

```bash
export APP_DB_MODE="postgresql"
export SPRING_DATASOURCE_URL="jdbc:postgresql://db.example.local:5432/iguana"
export SPRING_DATASOURCE_USERNAME="iguana"
export SPRING_DATASOURCE_PASSWORD="secret"
```

В этом режиме `spring-panel` использует единый primary datasource для runtime/user/bot/settings контуров, а `java-bot` получает тот же JDBC-контракт через переменные окружения и не должен инициализировать схему самостоятельно.

Для `java-bot` действует явная граница:

- в `APP_DB_MODE=sqlite` runtime сам поднимает local schema через `SqliteSchemaInitializer`;
- в `APP_DB_MODE=postgresql` runtime получает готовый PostgreSQL datasource-контракт и не несёт `SPRING_SQL_INIT_MODE`/`schema-sqlite.sql` в production-path.

Для `spring-panel` действует ещё одно правило:

- локальные `APP_DB_*` SQLite-пути автоматически подставляются только в явном `APP_DB_MODE=sqlite`;
- normal runtime path с `APP_DB_MODE=postgresql` больше не получает скрытый SQLite compatibility bootstrap через `EnvDefaultsInitializer`.

Для first-run bootstrap после стартового production-slice `01-183` действует ещё одно правило:

- default bootstrap-path должен завершаться в `PostgreSQL + RabbitMQ`;
- SQLite допускается только как явный compatibility override (`IGUANA_BOOTSTRAP_DB_MODE=sqlite`) или как аварийный fallback при явно включённом `IGUANA_BOOTSTRAP_ALLOW_SQLITE_FALLBACK=true`.

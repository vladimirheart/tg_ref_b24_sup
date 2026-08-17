# ⚙️ Конфигурация

Проект использует Spring Boot для панели и Java-бота. Настройки читаются из переменных окружения, `.env` (если загружаете его вручную) и JSON-файлов в `config/shared`.

## .env и переменные окружения

На свежем клоне можно не собирать `.env` вручную: `spring-panel/run-windows.bat`, `spring-panel/run-linux.sh` и скрипты `scripts/bootstrap-first-run.ps1` / `scripts/bootstrap-first-run.sh` теперь умеют создать его сами.

Если нужен ручной контроль, создайте файл `.env` по примеру ниже и дополните его нужными значениями:

```
TELEGRAM_BOT_TOKEN=123:ABC
GROUP_CHAT_ID=-1001234567890
APP_DB_MODE=sqlite
APP_DB_PANEL_RUNTIME=/srv/iguana/panel_runtime.db
APP_DB_PANEL_IDENTITY=/srv/iguana/panel_identity.db
APP_DB_BOT_RUNTIME=/srv/iguana/bot_runtime.db
APP_DB_CLIENTS=/srv/iguana/clients.db
APP_DB_KNOWLEDGE=/srv/iguana/knowledge_base.db
APP_DB_OBJECTS=/srv/iguana/objects.db
APP_DB_SETTINGS=/srv/iguana/settings.db
APP_BOT_DATABASE_DIR=/srv/iguana/bots
```

Для локального PostgreSQL-first старта bootstrap теперь использует такой базовый шаблон:

```bash
IGUANA_BOOTSTRAP_DB_MODE=auto
APP_POSTGRES_PORT=5432
APP_DB_MODE=postgresql
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/iguana
SPRING_DATASOURCE_USERNAME=iguana
SPRING_DATASOURCE_PASSWORD=iguana
```

Поведение bootstrap:

- `IGUANA_BOOTSTRAP_DB_MODE=auto` — на Windows сначала пытается автоматически поставить Docker Desktop через `winget`, затем предпочитает локальный PostgreSQL через Docker; если Docker всё равно недоступен, откатывается на SQLite dev mode;
- `IGUANA_BOOTSTRAP_DB_MODE=postgresql` — требует Docker и поднимает `docker-compose.local-postgres.yml`;
- `IGUANA_BOOTSTRAP_DB_MODE=sqlite` — оставляет локальный dev-path без Docker.

Ключевые переменные:

- `TELEGRAM_BOT_TOKEN` — токен Telegram-бота.
- `GROUP_CHAT_ID` — ID рабочей группы/чата для уведомлений (можно оставить пустым и сохранить в панели).
- `APP_DB_MODE` — явный режим БД: `auto`, `sqlite`, `postgresql`; для `spring-panel` режим `mysql` остаётся только как legacy-compatible external option.
- `APP_DB_PANEL_RUNTIME`, `APP_DB_PANEL_IDENTITY`, `APP_DB_BOT_RUNTIME` — канонические пути к основным SQLite-контурам.
- `APP_DB_TICKETS`, `APP_DB_USERS`, `APP_DB_BOT` — legacy aliases, которые пока остаются поддержаны.
- `APP_DB_*` для secondary-баз задают пути к клиентам, knowledge, объектам и registry-контуру.
- `APP_BOT_DATABASE_DIR` — каталог, в котором будут храниться отдельные базы для каждого бота.
- `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` — preferred-конфиг для external DB.
- `DATABASE_URL` — compatibility shorthand для external DB; для `java-bot` поддержан только PostgreSQL.
- `IGUANA_BOOTSTRAP_INSTALL_DOCKER` — разрешает bootstrap на Windows автоматически поставить Docker Desktop через `winget` (по умолчанию `true`).
- `IGUANA_BOOTSTRAP_ALLOW_SQLITE_FALLBACK` — разрешает в `IGUANA_BOOTSTRAP_DB_MODE=auto` откатиться на SQLite, если Docker так и не стал доступен после install/start попытки (по умолчанию `true`).
- `IGUANA_BOOTSTRAP_DOCKER_READY_TIMEOUT_SECONDS` — сколько ждать готовности Docker Desktop после установки/старта (по умолчанию `300` секунд).

Для целевого production-перехода по `01-181` используйте явный режим external DB:

```bash
APP_DB_MODE=postgresql
SPRING_DATASOURCE_URL=jdbc:postgresql://db.example.local:5432/iguana
SPRING_DATASOURCE_USERNAME=iguana
SPRING_DATASOURCE_PASSWORD=secret
```

Важно:

- `spring-panel` в external DB-режиме теперь сам выбирает vendor-specific Flyway migrations, а не SQLite-папку по умолчанию.
- `spring-panel` в external DB-режиме поднимает secondary/user/bot/settings datasources поверх primary JDBC-контура и не пытается создавать отдельные SQLite-файлы для этих ролей.
- `java-bot` больше не использует Spring Boot `sql.init` как runtime-механику владения схемой: в external PostgreSQL-режиме бот просто подключается к готовой схеме, а не пытается инициализировать её сам.
- `java-bot` в SQLite-режиме теперь поднимает local schema явным `SqliteSchemaInitializer`, который исполняет `schema-sqlite.sql` только для local/dev-контура.
- runtime-контракт запуска ботов теперь пробрасывает PostgreSQL env (`APP_DB_MODE`, `SPRING_DATASOURCE_*`) напрямую из панели, а SQLite-пути используются только в явном `sqlite`-режиме без дополнительных `SPRING_SQL_INIT_*` флагов.

> 💡 ID группы поддержки для Telegram можно сохранить в панели администратора в разделе «Каналы (боты)». Если оставить пустым, бот запишет ID автоматически после добавления в чат.

## Общие JSON-настройки

Файлы `config/shared/settings.json`, `config/shared/locations.json` и `config/shared/org_structure.json` используются панелью и ботом. При изменении JSON-файлов перезапуск сервисов не требуется — они читаются напрямую с диска.

## `dialog_config`: SLA-эскалация через webhook

В `settings.json -> dialog_config` можно включить серверные webhook-уведомления для критичных нераспределённых диалогов:

- `sla_critical_escalation_enabled` — включает саму SLA-эскалацию (по умолчанию `true`).
- `sla_critical_escalation_webhook_enabled` — включает отправку webhook (по умолчанию `false`).
- `sla_critical_escalation_webhook_url` — legacy-поле с одним URL получателя webhook (обратная совместимость).
- `sla_critical_escalation_webhook_urls` — список URL для fan-out отправки в несколько incident-каналов (рекомендуемый формат).
- `sla_critical_escalation_webhook_cooldown_minutes` — минимальный интервал повторной отправки по одному тикету (по умолчанию `30`).
- `sla_critical_escalation_webhook_timeout_ms` — timeout HTTP-вызова webhook (по умолчанию `4000`).
- `sla_critical_minutes` — порог критичности SLA (используется для отбора тикетов).
- `sla_target_minutes` — целевой SLA в минутах (используется для расчёта `minutes_left`).

Фоновая проверка выполняется по расписанию (`panel.sla-escalation.webhook-check-interval-ms`, по умолчанию 120000 мс).

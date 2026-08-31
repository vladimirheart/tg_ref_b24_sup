# SQLite-only perimeter для `01-181`

Документ фиксирует, какие остаточные SQLite-компоненты на текущем этапе считаются допустимыми после перехода к PostgreSQL-first runtime path.

Цель этого perimeter:

- не пускать legacy SQLite bootstrap обратно в active external PostgreSQL path;
- оставить локальный dev/onboarding сценарий воспроизводимым;
- явно отделить временный local bootstrap слой от production ownership PostgreSQL-схемы.

## 1. Что разрешено оставлять SQLite-only

### `spring-panel`

- `DatabaseBootstrapService`
  Создаёт `clients.db`, `knowledge_base.db` и `objects.db` только в `APP_DB_MODE=sqlite`.
- `MonitoringDatabaseBootstrapService`
  Поддерживает локальный `monitoring.db` и перенос старых monitoring-таблиц только в SQLite-режиме.
- `LegacyBotShardConsolidationService`
  В PostgreSQL runtime backend-owned образом переносит данные из legacy `bot-<channelId>.db` в canonical таблицы; live bootstrap per-channel shard-файлов больше не считается допустимым runtime ownership слоем.
- `SqliteSchemaBootstrapSupport`
  Общий helper для local SQLite schema bootstrap.
- `EnvDefaultsInitializer`
  Подставляет локальные `APP_DB_*` пути только при явном `app.datasource.mode=sqlite`.
- `IGUANA_LEGACY_SQLITE_AUTO_IMPORT=true`
  Явно включает one-time compatibility import/recovery из legacy SQLite только по осознанному запросу.
- SQLite Flyway migrations
  Владеют compatibility-таблицами вроде `password_reset_requests`, `chat_attachment_metadata` и `ui_event_outbox`, чтобы live-beans не создавали их по месту использования.

### `java-bot`

- `SqliteSchemaInitializer`
  Явно поднимает `schema-sqlite.sql` только в `APP_DB_MODE=sqlite`.
- `SqliteTriggerInitializer`
  Поддерживает SQLite-trigger/feedback bootstrap только в local SQLite path.
- `schema-sqlite.sql`
  Разрешён только как local/dev bootstrap ресурс.

## 2. Что в этот perimeter больше входить не должно

В external PostgreSQL path запрещены:

- runtime `ALTER TABLE`, `CREATE TABLE IF NOT EXISTS`, `INSERT OR IGNORE`, SQLite `PRAGMA`;
- создание или миграция business-таблиц из `java-bot`;
- зависимость запуска бота от `schema-sqlite.sql`, `SPRING_SQL_INIT_MODE`, `spring.sql.init.platform`;
- любой новый first-run bootstrap через `scripts/bootstrap-first-run.ps1` / `scripts/bootstrap-first-run.sh`, который пытается вернуть `.env` к SQLite-режиму или автоматически ротировать persisted infra credentials без отдельной migration-процедуры;
- неявное создание `bot-<channelId>.db`, `monitoring.db` или secondary SQLite-файлов при `APP_DB_MODE=postgresql`;
- operator-facing live reads, которые в `APP_DB_MODE=postgresql` продолжают напрямую открывать per-channel SQLite-файлы вместо canonical datasource;
- schema ownership вспомогательных SQLite-таблиц внутри live controller/service bean’ов (`PasswordResetRequestApiController`, `UiEventOutboxWatcher`, `ChatAttachmentMetadata*`);
- автоматический import/recovery из legacy `*.db` в обычном PostgreSQL runtime без backend-owned marker/guardrail механики;
- перенос business ownership обратно в local SQLite только ради удобства dev-старта.

## 3. Практический смысл

На текущем этапе `01-181` PostgreSQL уже считается canonical runtime path для fresh external окружения.

SQLite остаётся только в трёх ролях:

- local/dev bootstrap;
- manual compatibility launch с явным `APP_DB_MODE=sqlite`, а не first-run bootstrap;
- explicit one-time import/recovery flow, а не always-on production helper;
- legacy/test perimeter, который не должен участвовать в external production-like path.

Если новый код требует SQLite-ветку вне этих ролей, это уже не continuation existing perimeter, а новый architectural debt и его нужно отдельно обосновывать.

## 4. Операционный чек

Перед тем как считать новый шаг совместимым с `01-181`, достаточно проверить:

- при `APP_DB_MODE=postgresql` runtime не выполняет SQLite DDL/trigger bootstrap;
- Flyway остаётся единственным владельцем PostgreSQL schema;
- first-run bootstrap создаёт только локальный PostgreSQL/RabbitMQ контур и больше не генерирует SQLite `.env`;
- новый SQLite code path явно gated через runtime mode и документирован как local/dev only.

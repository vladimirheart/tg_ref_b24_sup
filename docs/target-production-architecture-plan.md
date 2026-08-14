# План целевой production-архитектуры Iguana

Документ фиксирует обязательный стартовый audit для задачи `01-181` по фактическому состоянию репозитория на `2026-08-11` и задаёт безопасный порядок перехода к production-архитектуре без параллельного развития двух несовместимых source-of-truth.

## 1. Текущие DB writers

### `spring-panel`

- `panel_runtime.db`
  Используется как primary `DataSource`/JPA-контур через `SqliteDataSourceConfiguration`.
  Сюда пишутся диалоги, сообщения, задачи, каналы, уведомления, knowledge/article-сущности, client-related runtime-таблицы и большая часть operator-facing business state.
- `panel_identity.db`
  Отдельный users/roles/auth-контур через `UsersSqliteDataSourceConfiguration` и `usersJdbcTemplate`.
- `monitoring.db`
  Отдельный monitoring-контур через `MonitoringSqliteDataSourceConfiguration` и `monitoringJdbcTemplate`.
- `clients.db`, `knowledge_base.db`, `objects.db`
  Создаются и bootstrap-ятся в `DatabaseBootstrapService`.
- `settings.db`
  Используется `BotDatabaseRegistry` для registry/linking таблиц `database_registry`, `bot_instances`, `database_links`.
- `bot-<channelId>.db`
  Панель создаёт per-channel SQLite-файлы через `BotDatabaseRegistry.ensureBotDatabase(...)`.

### `java-bot`

- По умолчанию `bot-core` всё ещё читает/пишет в `panel_runtime.db` через `support-bot.database.path`, а не в `bot_runtime.db` как в единственный transport runtime.
- Для SQLite-режима бот продолжает инициализировать локальную схему через `schema-sqlite.sql`.
- Для external PostgreSQL-режима после текущего шага `01-181` бот больше не должен владеть схемой вообще: production-path должен подключаться только к уже подготовленной PostgreSQL-схеме, а локальный SQLite bootstrap остаётся отдельным dev-only механизмом.

## 2. Текущие DB readers

- `spring-panel` читает:
  - `panel_runtime.db` как основной business/source-of-truth контур UI;
  - `panel_identity.db` для auth, профилей, ролей, маршрутизации уведомлений;
  - `monitoring.db` для RMS/SSL/iiko monitoring;
  - `bot_runtime.db` и `bot-<channelId>.db` для bot-side history/unblock/runtime-хвостов;
  - `objects.db` для паспортов объектов;
  - transitional `clients.db`, `knowledge_base.db`, `settings.db`.
- `java-bot` читает:
  - `panel_runtime.db` по умолчанию;
  - SQLite schema/init resources для локального режима;
  - shared JSON-конфиг и файловые каталоги вложений.

## 3. Фактические source of truth

- `panel_runtime.db`
  Реальный business source of truth для support workflow и большинства operator-facing данных.
- `panel_identity.db`
  Канонический identity/access source of truth.
- `monitoring.db`
  Канонический monitoring source of truth.
- `objects.db`
  Пока остаётся отдельным активным контуром, хотя в target-state должен быть поглощён общим business storage.
- `bot_runtime.db`
  Активный, но ещё не доведённый до чистого transport/runtime-контура.
- `settings.db`
  Transitional registry, не business source of truth.
- `bot-<channelId>.db`
  Legacy/shard слой, не должен развиваться как самостоятельная доменная БД.

## 4. Mapping текущих контуров к target-state

| Текущий контур | Target-state |
| --- | --- |
| `panel_runtime.db` | `PostgreSQL.core` для диалогов, тикетов, сообщений, задач, notifications, knowledge, client-facing operational state |
| `panel_identity.db` | `PostgreSQL.identity` |
| `monitoring.db` | `PostgreSQL.monitoring` |
| `objects.db` | `PostgreSQL.objects` |
| `clients.db` | поглотить в `PostgreSQL.core` |
| `knowledge_base.db` | поглотить в `PostgreSQL.knowledge` или `PostgreSQL.core` по фактическому ownership |
| `settings.db` | убрать как отдельный runtime-contour; registry metadata либо перенести в `PostgreSQL.integrations`, либо удалить |
| `bot_runtime.db` | оставить только как transport/runtime contour до полного перехода на RabbitMQ + backend-owned business writes |
| `bot-<channelId>.db` | убрать как отдельный source of truth; при необходимости оставить только как runtime spool/shard abstraction |

## 5. Текущая process model ботов

- Панель запускает процессы ботов через `BotProcessService`.
- Контракт окружения ботов формируется `BotRuntimeContractService`.
- Registry per-channel SQLite-файлов ведёт `BotDatabaseRegistry`.
- `java-bot` сейчас может работать напрямую с business SQLite-контуром панели, что несовместимо с целевой production-моделью `provider -> worker -> queue/api -> backend -> PostgreSQL`.

## 6. Ключевые migration risks

- Прямой доступ `java-bot` к business DB смешивает transport и business ownership.
- `settings.db` и `bot-<channelId>.db` продолжают закреплять legacy topology и усложняют миграцию.
- Часть bootstrap-логики использует SQLite-specific SQL (`datetime('now')`, `INSERT OR IGNORE`, SQLite schema bootstrap).
- `spring-panel` имеет split между primary/runtime, identity, monitoring и secondary DB, поэтому миграция к одной PostgreSQL БД со schema boundaries потребует переезда именованных `JdbcTemplate` и bootstrap-сервисов.
- В `spring-panel` `testCompile` уже сломан несвязанными тестами, что ограничивает быструю автоматическую верификацию архитектурных изменений.

## 7. Legacy components, которые должны исчезнуть

- Прямая зависимость `java-bot` от `panel_runtime.db` как default business DB.
- Runtime-рост `settings.db` как отдельного registry-контура.
- Per-channel `bot-<channelId>.db` как скрытый доменный storage.
- SQLite-specific bootstrap для внешней production DB.
- Неявное переключение панели на external DB только по факту наличия `DATABASE_URL`.

## 8. Compatibility points, которые нужно удержать в переходе

- Текущие `APP_DB_*` env aliases для SQLite-режима.
- `APP_DB_TICKETS`, `APP_DB_USERS`, `APP_DB_BOT` как compatibility aliases.
- Существующий запуск `spring-panel` и `java-bot` на локальной SQLite without extra infra.
- Текущие JSON-конфиги и файловые каталоги вложений.

## 9. Что уже сделано первым implementation-срезом в `01-181`

- Добавлен явный `APP_DB_MODE` / `app.datasource.mode` для `spring-panel` и `support-bot.database.mode` для `java-bot` с режимами `auto`, `sqlite`, `postgresql` (для панели также `mysql` как legacy-compatible external mode).
- `spring-panel` теперь при external DB выбирает корректный `spring.flyway.locations` под vendor вместо молчаливого использования SQLite migrations.
- `java-bot` в external PostgreSQL-режиме теперь не использует `spring.sql.init` вовсе, чтобы transport worker не выступал владельцем business schema.

## 10. Фазовый план

### Phase 0. Contract hardening

- Явный выбор DB mode.
- Явное разделение SQLite-dev режима и external production-like режима.
- Запрет schema ownership у `java-bot` для external PostgreSQL.

### Phase 1. Storage ownership cleanup

- Перевести `java-bot` с default `panel_runtime.db` на явный transport contract.
- Остановить дальнейший рост `settings.db` и `bot-<channelId>.db`.
- Зафиксировать backend-only ownership для business writes в правилах и runtime контрактах.

### Phase 2. PostgreSQL schema bridge

- Ввести canonical PostgreSQL schemas `core`, `identity`, `monitoring`, `objects`, `integrations`, `audit`.
- Перенести Flyway ownership PostgreSQL-структуры в `spring-panel`.
- Подготовить data migration utilities из текущих SQLite-контуров.

### Phase 3. Integration decoupling

- Увести bot-side ingress из прямого JDBC в очередь/API boundary.
- Ввести outbox/inbox контур и транспортную idempotency-модель.

### Phase 4. Runtime infra

- Redis для session/runtime coordination.
- RabbitMQ для integration transport.
- MinIO/S3 abstraction для attachment binaries.

### Phase 5. Legacy removal

- Убрать production-зависимость от business SQLite.
- Свести SQLite к dev/bootstrap/runtime-spool обязанностям либо полностью убрать из production path.

## 10.1. Текущий допустимый SQLite-only perimeter

После уже сделанных шагов по `01-181` SQLite разрешён только как local/dev bootstrap слой.

Сейчас к этому perimeter относятся:

- `spring-panel`: `DatabaseBootstrapService`, `MonitoringDatabaseBootstrapService`, `BotDatabaseRegistry`, `SqliteSchemaBootstrapSupport`;
- `java-bot`: `SqliteSchemaInitializer`, `SqliteTriggerInitializer`, `schema-sqlite.sql`;
- first-run fallback bootstrap, который оставляет локальный запуск в `APP_DB_MODE=sqlite`, если на машине нет Docker для локального PostgreSQL.

Этот слой больше не должен участвовать в external PostgreSQL runtime path. Для отдельной фиксации perimeter см. [docs/SQLITE_BOOTSTRAP_PERIMETER.md](SQLITE_BOOTSTRAP_PERIMETER.md).

## 11. Рекомендуемый следующий технический шаг

Следующим implementation-шагом после текущего среза нужно перевести `BotRuntimeContractService` и runtime launch model на явное разграничение:

- SQLite local/dev path;
- external PostgreSQL backend path без schema ownership у бота;
- будущий queue/API transport path как целевой production режим.

# План целевой production-архитектуры Iguana

Документ фиксирует обязательный стартовый audit для задачи `01-181` по фактическому состоянию репозитория на `2026-08-11` и задаёт безопасный порядок перехода к production-архитектуре без параллельного развития двух несовместимых source-of-truth.

## 1. Текущие DB writers

### `spring-panel`

- `panel_runtime.db`
  Используется как primary `DataSource`/JPA-контур через `SqliteDataSourceConfiguration`.
  Сюда пишутся диалоги, сообщения, задачи, каналы, уведомления, knowledge/article-сущности, client-related runtime-таблицы и большая часть operator-facing business state.
- `panel_identity.db`
  Отдельный users/roles/auth-контур в historical topology; в external runtime `usersJdbcTemplate` уже должен опираться на primary contour, а SQLite identity path оставаться только compatibility-режимом.
- `monitoring.db`
  Historical monitoring split; в external runtime live monitoring reads/writes уже должны идти через primary contour, а `monitoring.db` оставаться только compatibility/bootstrap слоем.
- `clients.db`, `knowledge_base.db`, `objects.db`
  Создаются только в explicit SQLite compatibility path через `DatabaseBootstrapService`.
- `bot-<channelId>.db`
  Per-channel shard-файлы больше не должны создаваться как normal runtime path; legacy данные из них должны переноситься в canonical contour backend-owned consolidation step.

### `java-bot`

- Default `bot-core` contract больше не должен неявно тянуться к `panel_runtime.db`: shared panel SQLite path теперь передаётся только через явный `SUPPORT_BOT_DATABASE_PATH`, а не как implicit default.
- Для SQLite-режима бот продолжает инициализировать локальную схему через `schema-sqlite.sql`.
- Для external PostgreSQL-режима после текущего шага `01-181` бот больше не должен владеть схемой вообще: production-path должен подключаться только к уже подготовленной PostgreSQL-схеме, а локальный SQLite bootstrap остаётся отдельным dev-only механизмом.

## 2. Текущие DB readers

- `spring-panel` читает:
  - `panel_runtime.db` как основной business/source-of-truth контур UI;
  - `panel_identity.db` только как compatibility path; live auth/profile/runtime reads в external mode уже должны идти через primary contour;
  - `monitoring.db` только как SQLite compatibility/bootstrap слой, тогда как live monitoring runtime в external mode уже идёт через primary contour;
  - `bot_runtime.db` только для remaining compatibility/runtime-хвостов, а `bot-<channelId>.db` уже не live owner, а import-only legacy source;
  - `objects.db`, `clients.db`, `knowledge_base.db` только как explicit SQLite compatibility/bootstrap perimeter.
- `java-bot` читает:
  - explicit SQLite compatibility path через `SUPPORT_BOT_DATABASE_PATH`, если он действительно прокинут;
  - иначе `bot_runtime.db` / `APP_DB_BOT_RUNTIME` как собственный compatibility/runtime fallback;
  - SQLite schema/init resources для локального режима;
  - shared JSON-конфиг и файловые каталоги вложений.

## 3. Фактические source of truth

- `panel_runtime.db`
  Реальный business source of truth для support workflow и большинства operator-facing данных.
- `panel_identity.db`
  Historical identity contour; в external runtime canonical source of truth должен быть уже в основном PostgreSQL contour.
- `monitoring.db`
  Historical monitoring contour; в external runtime canonical source of truth должен быть уже в основном PostgreSQL contour.
- `objects.db`
  Historical object contour; physical datasource split уже ослаблен и live reads/writes в external runtime должны опираться на canonical datasource.
- `bot_runtime.db`
  Shared bot/runtime contour уже не поднимается как отдельный live Spring
  datasource в external runtime, но как compatibility/transport слой ещё не
  доведён до конечной RabbitMQ-first модели.
- `bot-<channelId>.db`
  Legacy/import-only слой, не должен развиваться как самостоятельная доменная БД и не должен участвовать в live runtime reads/writes.
- `incidents`
  Новый canonical backend-owned domain для incident lifecycle, relations, watchers, routes и history.

## 4. Mapping текущих контуров к target-state

| Текущий контур | Target-state |
| --- | --- |
| `panel_runtime.db` | `PostgreSQL.core` для диалогов, тикетов, сообщений, задач, notifications, knowledge, client-facing operational state |
| `panel_identity.db` | `PostgreSQL.identity` |
| `monitoring.db` | `PostgreSQL.monitoring` |
| `objects.db` | `PostgreSQL.objects` |
| `clients.db` | поглотить в `PostgreSQL.core` |
| `knowledge_base.db` | поглотить в `PostgreSQL.knowledge` или `PostgreSQL.core` по фактическому ownership |
| `bot_runtime.db` | оставить только как compatibility/runtime contour до полного перехода на RabbitMQ + backend-owned business writes |
| `bot-<channelId>.db` | убрать как отдельный source of truth; допустим только как временный import source для legacy-данных |
| `incidents` domain | canonical PostgreSQL contour для incident lifecycle и связей с dialogs/tasks/objects |

## 5. Текущая process model ботов

- Панель запускает процессы ботов через `BotProcessService`.
- Контракт окружения ботов формируется `BotRuntimeContractService`.
- Ownership per-channel SQLite shard-файлов переведён в backend-owned consolidation path; automatic shard bootstrap больше не считается допустимым runtime path.
- `java-bot` в live `rabbitmq` contour уже не должен восприниматься как допустимый owner business state; прямой SQLite/JDBC bridge остаётся только compatibility path и несовместим с целевой production-моделью `provider -> worker -> queue/api -> backend -> PostgreSQL`.

## 6. Ключевые migration risks

- Даже после cleanup-а explicit SQLite compatibility bridge у `java-bot` остаётся architectural debt, если его ошибочно воспринимать как production path.
- Legacy данные в `bot-<channelId>.db` ещё требуют controlled import и operational cleanup, но сам shard-layer уже выведен из live runtime topology.
- `bot_runtime.db` уже ослаблен как physical datasource split, но final RabbitMQ-first runtime ownership всё ещё не закрыт полностью.
- Часть bootstrap-логики использует SQLite-specific SQL (`datetime('now')`, `INSERT OR IGNORE`, SQLite schema bootstrap).
- attachment/object-storage contour пока ещё не доведён до обязательного MinIO/S3 production contract.
- В `spring-panel` `testCompile` уже сломан несвязанными тестами, что ограничивает быструю автоматическую верификацию архитектурных изменений.

## 7. Legacy components, которые должны исчезнуть

- Прямая зависимость `java-bot` от `panel_runtime.db` как default business DB.
- Возврат `bot_runtime.db` в роль отдельного live datasource source of truth для panel-side runtime.
- Per-channel `bot-<channelId>.db` как скрытый доменный storage или live runtime bridge.
- SQLite-specific bootstrap для внешней production DB.
- Неявное переключение панели на external DB только по факту наличия `DATABASE_URL`.

## 8. Compatibility points, которые нужно удержать в переходе

- Текущие `APP_DB_*` env aliases для SQLite-режима.
- `APP_DB_TICKETS`, `APP_DB_USERS`, `APP_DB_BOT` как compatibility aliases.
- Существующий запуск `spring-panel` и `java-bot` на локальной SQLite without extra infra.
- Текущие JSON-конфиги и файловые каталоги вложений.

## 9. Что уже сделано первым implementation-срезом в `01-181`

- Добавлен явный `APP_DB_MODE` / `app.datasource.mode` для `spring-panel` и `support-bot.database.mode` для `java-bot`; normal runtime default переведён в `postgresql`, `sqlite` оставлен только явным compatibility-режимом, `auto` сохраняется лишь как transitional/manual option (для панели также `mysql` как legacy-compatible external mode).
- `spring-panel` теперь при external DB выбирает корректный `spring.flyway.locations` под vendor вместо молчаливого использования SQLite migrations.
- `java-bot` в external PostgreSQL-режиме теперь не использует `spring.sql.init` вовсе, чтобы transport worker не выступал владельцем business schema.

## 10. Фазовый план

### Phase 0. Contract hardening

- Явный выбор DB mode.
- Явное разделение SQLite-dev режима и external production-like режима.
- Запрет schema ownership у `java-bot` для external PostgreSQL.

### Phase 1. Storage ownership cleanup

- Перевести `java-bot` с default `panel_runtime.db` на явный transport contract.
- Удерживать `bot-<channelId>.db` только как read-only import source до полного operational cleanup.
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

- `spring-panel`: `DatabaseBootstrapService`, `MonitoringDatabaseBootstrapService`, `LegacyBotShardConsolidationService`, `SqliteSchemaBootstrapSupport`;
- `java-bot`: `SqliteSchemaInitializer`, `SqliteTriggerInitializer`, `schema-sqlite.sql`;
- explicit SQLite compatibility bootstrap, который включается только при сознательном выборе `IGUANA_BOOTSTRAP_DB_MODE=sqlite` или аварийном override.

Этот слой больше не должен участвовать в external PostgreSQL runtime path. Для отдельной фиксации perimeter см. [docs/SQLITE_BOOTSTRAP_PERIMETER.md](SQLITE_BOOTSTRAP_PERIMETER.md).

## 10.2. Текущий допустимый JDBC-only compatibility perimeter в `java-bot`

После cleanup-среза `01-182` bot-side business fallback в `java-bot` допустим только как явный local/dev compatibility слой.

Сейчас к этому perimeter относятся:

- явные `runtime-mode` ветки в `TicketService`, которые продолжают обслуживать только legacy/local `jdbc`-маршрут;
- `ChatHistoryService` как локальный compatibility write/edit path для этого же `jdbc`-режима;
- `TaskService` и `AutoCloseFollowUpTaskService` только как `jdbc`-only beans, которые больше не поднимаются в `rabbitmq`-контуре.

Что больше не допускается:

- активный bot-side task/follow-up business path в `rabbitmq`-режиме;
- неявное существование `TaskService` / `AutoCloseFollowUpTaskService` как production-like beans рядом с backend-owned transport path;
- скрытое восприятие legacy bot-side task lifecycle как части целевой production-модели.

В `rabbitmq`-контуре вместо старого follow-up business bean теперь допускается только явный no-op boundary, чтобы transport runtime не возвращал ownership задач обратно в `java-bot`.

## 11. Текущий статус и следующий scope

После уже выполненных шагов `01-181` ближайший recommended step больше не в том, чтобы ещё раз уточнять `BotRuntimeContractService`: это разграничение уже зафиксировано и в коде, и в runtime-диагностике.

Практически readiness-часть на текущем этапе можно считать закрытой для fresh-start PostgreSQL-first запуска:

- SQLite local/dev path явно отделён от external PostgreSQL path;
- `BotRuntimeContractService` и `/api/bots/{channelId}/runtime-contract` больше не считают SQLite production-ready сценарием;
- external PostgreSQL runtime больше не должен владеть схемой, запускать SQLite bootstrap или выполнять runtime DDL.

Дальше разумнее открывать уже отдельный scope, а не продолжать смешивать его с readiness-срезом:

- `SQLite -> PostgreSQL` migration/backfill utility;
- transport/backend ownership split до целевой схемы `provider -> worker -> queue/api -> backend -> PostgreSQL`;
- инфраструктурный production contour (`Redis`, `RabbitMQ`, `MinIO`, leases, multi-worker runtime).

Итоговый close-out по readiness-части зафиксирован отдельно в [docs/POSTGRESQL_FIRST_READINESS_CLOSEOUT.md](POSTGRESQL_FIRST_READINESS_CLOSEOUT.md).

## 11.1. Фактический production contour на 2026-08-19

После пакетов `01-183` target-план уже нельзя читать как чисто будущий roadmap: значимая часть contour стала фактом репозитория.

- canonical live contour теперь читается как `PostgreSQL + Redis + RabbitMQ + object storage + backend-owned incident/workbench layer`;
- shared schedulers, transport inbox/outbox, route deliveries и signal incidents уже работают через leases / claims / retry semantics;
- instance-local round-robin routing и SLA webhook cooldown больше не должны определять shared live behavior;
- operator-facing production runbook для этого состояния вынесен в [docs/runbooks/postgresql-production-contour.md](runbooks/postgresql-production-contour.md).

Что всё ещё важно удерживать как явный operational invariant:

- bot-side conversational ingress state пока остаётся process-local;
- для одного и того же канала нужен singleton/sticky deployment policy, пока этот слой не externalized в shared runtime storage.

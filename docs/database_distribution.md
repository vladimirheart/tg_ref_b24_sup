# Распределение БД в проекте Iguana

## Назначение документа

Этот документ фиксирует фактическое распределение баз данных в проекте
`Iguana` на текущий момент. В отличие от target-state документов, здесь
описано не желаемое будущее состояние, а реальный runtime wiring:

- какие SQLite-файлы подключаются сейчас;
- какие `DataSource` и `JdbcTemplate` их обслуживают;
- какие модули и сервисы используют каждую БД;
- где контур уже является каноническим, а где он остаётся transitional или
  legacy.

Документ актуализирован по коду репозитория на `9 июля 2026`.

## Короткое резюме

Сейчас в проекте задекларировано несколько SQLite-контуров, но их роль разная:

- `panel_runtime.db` - главный operational runtime панели и фактический
  source of truth для большинства бизнес-таблиц;
- `panel_identity.db` - отдельная БД пользователей, ролей и auth-контуров;
- `monitoring.db` - отдельный monitoring-контур;
- `bot_runtime.db` - shared bot/runtime compatibility-контур, который больше
  не поднимается как отдельный live Spring datasource в external runtime;
- `objects.db` - реально используемый отдельный контур паспортов объектов;
- `clients.db`, `knowledge_base.db`, `settings.db` - transitional/registry
  контуры, из которых не все являются текущим business source of truth.

Отдельно существует каталог `bot-<channelId>.db`, который создаётся панелью
для channel-local bot файлов.

## 1. Текущее распределение БД

| Логический контур | Физический файл | Spring property / env | Кто подключает | Как используется сейчас | Статус |
| --- | --- | --- | --- | --- | --- |
| `panel-runtime` | `panel_runtime.db` | `app.datasource.sqlite.path` / `APP_DB_PANEL_RUNTIME` | `spring-panel`, `java-bot` | Главная runtime БД панели, JPA + primary `JdbcTemplate` | canonical |
| `panel-identity` | `panel_identity.db` | `app.datasource.users-sqlite.path` / `APP_DB_PANEL_IDENTITY` | `spring-panel` | Пользователи, роли, auth/read-write через `usersJdbcTemplate` | canonical |
| `monitoring` | `monitoring.db` | `app.datasource.monitoring-sqlite.path` / `APP_DB_MONITORING` | `spring-panel` | SQLite compatibility/bootstrap контур; в external runtime monitoring-domain идёт через primary PostgreSQL contour | compatibility |
| `bot-runtime` | `bot_runtime.db` | `app.datasource.bot-sqlite.path` / `APP_DB_BOT_RUNTIME` | `spring-panel` | Lazy SQLite compatibility/shared-bot contour; external runtime больше не поднимает отдельный Spring datasource bean | transitional |
| `clients` | `clients.db` | `app.datasource.clients-sqlite.path` / `APP_DB_CLIENTS` | `spring-panel` | Bootstrap secondary БД клиентов | transitional |
| `knowledge` | `knowledge_base.db` | `app.datasource.knowledge-sqlite.path` / `APP_DB_KNOWLEDGE` | `spring-panel` | Bootstrap secondary knowledge БД | transitional |
| `objects` | `objects.db` | `app.datasource.objects-sqlite.path` / `APP_DB_OBJECTS` | `spring-panel` | Отдельный контур паспортов объектов | active |
| `settings-registry` | `settings.db` | `app.datasource.settings-sqlite.path` / `APP_DB_SETTINGS` | `spring-panel` | Registry/linking для DB-метаданных и bot instance mapping | transitional |
| `bot shard layer` | `bot-<channelId>.db` | `APP_BOT_DATABASE_DIR` | `spring-panel` | Per-channel bot файлы, создаются `BotDatabaseRegistry` | shard/legacy layer |

## 2. Как БД подключаются в `spring-panel`

### 2.1. Primary runtime: `panel_runtime.db`

Главная БД панели поднимается через:

- `spring-panel/src/main/resources/application.yml`
- `SqliteDataSourceConfiguration`
- `SqliteDataSourceProperties`

Путь задаётся так:

```yaml
app:
  datasource:
    sqlite:
      path: ${APP_DB_PANEL_RUNTIME:${APP_DB_TICKETS:panel_runtime.db}}
```

Этот `DataSource` является `@Primary`, а значит:

- JPA-репозитории по умолчанию работают именно с ним;
- основной `JdbcTemplate` панели также смотрит в него;
- большая часть `entity` и `repository` живёт на этом контуре.

### 2.2. Identity runtime: `panel_identity.db`

Отдельный users/roles/auth-контур поднимается через:

- `UsersSqliteDataSourceConfiguration`
- `UsersSqliteDataSourceProperties`
- бин `usersJdbcTemplate`

Путь задаётся так:

```yaml
app:
  datasource:
    users-sqlite:
      path: ${APP_DB_PANEL_IDENTITY:${APP_DB_USERS:panel_identity.db}}
```

С этим контуром работают:

- `UserRepositoryUserDetailsService`
- `AuthManagementApiController`
- security bootstrap вокруг `users`, `roles`, `user_authorities`

### 2.3. Monitoring runtime: `monitoring.db`

Monitoring-контур поднимается через:

- `MonitoringSqliteDataSourceConfiguration`
- `MonitoringSqliteDataSourceProperties`
- бин `monitoringJdbcTemplate`
- бин `monitoringRuntimeJdbcTemplate`

Путь задаётся так:

```yaml
app:
  datasource:
    monitoring-sqlite:
      path: ${APP_DB_MONITORING:monitoring.db}
```

С этим контуром работают:

- `MonitoringDatabaseBootstrapService`

Runtime-смысл теперь разный по режимам:

- в `APP_DB_MODE=sqlite` monitoring-domain продолжает работать через отдельный `monitoring.db`;
- в `APP_DB_MODE=postgresql` live monitoring repositories переключаются на primary datasource через `monitoringRuntimeJdbcTemplate`;
- отдельный `monitoringJdbcTemplate` остаётся только для SQLite bootstrap/migration слоя.

### 2.4. Bot runtime: `bot_runtime.db`

Bot-контур для панели теперь больше не поднимается как отдельный live
datasource bean. Через Spring остаётся только properties-holder:

- `BotSqliteDataSourceConfiguration`
- `BotSqliteDataSourceProperties`

Путь задаётся так:

```yaml
app:
  datasource:
    bot-sqlite:
      path: ${APP_DB_BOT_RUNTIME:${APP_DB_BOT:bot_runtime.db}}
```

Runtime-смысл теперь разный по режимам:

- в `APP_DB_MODE=sqlite` `DatabaseBootstrapService` лениво создаёт локальный
  SQLite datasource для `bot_runtime.db` и bootstrap-ит compatibility-таблицы;
- в `APP_DB_MODE=postgresql` отдельный `botDataSource` / `botJdbcTemplate`
  больше не поднимаются, а panel-side runtime продолжает жить через canonical
  primary contour и internal API boundary.

Важно: панель всё ещё умеет bootstrap-ить `bot_runtime.db` в explicit
compatibility path, но запуск самих `java-bot` процессов сейчас ориентирован
прежде всего на `panel_runtime.db`, а не на `bot_runtime.db` как единственный
runtime source.

### 2.5. Secondary/legacy контуры

Через Spring datasource graph больше не поднимаются отдельные legacy
`clients`/`knowledge`/`settings`/`objects` data source beans.

Отдельно:

- `clients.db` и `knowledge_base.db` больше не поднимаются как общие Spring
  datasources и создаются только лениво из `DatabaseBootstrapService` в явном
  SQLite compatibility path;
- `settings.db` также не поднимается как общий Spring datasource: его lazy
  SQLite access остаётся только внутри `BotDatabaseRegistry` и только для
  явного compatibility path.
- `objects.db` тоже больше не поднимается как отдельный общий Spring
  datasource: `ObjectPassportService` использует primary datasource в external
  runtime и ленивый SQLite datasource только в explicit compatibility path.

## 3. Фактическое владение данными по БД

### 3.1. `panel_runtime.db`

Это фактический центр проекта. Здесь живут или фактически читаются:

- `tickets`
- `messages`
- `chat_history`
- `channels`
- `tasks`, `task_*`
- `notifications`
- `ticket_active`, `ticket_responsibles`, `ticket_spans`
- `pending_feedback_requests`
- `client_statuses`
- `client_blacklist`
- `client_unblock_requests`
- `client_phones`, `client_usernames`, `client_avatar_history`
- `web_form_sessions`
- `knowledge_articles`, `knowledge_article_files`
- `app_settings`
- `settings_parameters`

Это видно по двум признакам:

- primary JPA-репозитории по умолчанию работают с `@Primary DataSource`;
- основные сервисы и SQL-запросы панели используют обычный `JdbcTemplate`,
  а не отдельные legacy bootstrap contours `clients.db` или `knowledge_base.db`.

Примеры:

- `DialogLookupReadService` читает `client_statuses`;
- `DialogConversationReadService` и `DialogReplyTargetService` работают с
  `web_form_sessions`;
- `KnowledgeBaseService` использует JPA-репозитории
  `KnowledgeArticleRepository` и `KnowledgeArticleFileRepository`, а значит
  фактически живёт на primary runtime DB.

Вывод: `panel_runtime.db` - реальный business source of truth панели.

### 3.2. `panel_identity.db`

Здесь сосредоточен отдельный identity/access контур:

- `users`
- `roles`
- `user_authorities`
- auth-related данные
- при JDBC session storage - также `SPRING_SESSION` и
  `SPRING_SESSION_ATTRIBUTES`

Эта БД используется отдельно от business runtime и обслуживается
`usersJdbcTemplate`.

### 3.3. `monitoring.db`

Здесь сосредоточен monitoring-контур:

- `ssl_certificate_monitors`
- `rms_license_monitors`
- `iiko_api_monitors`
- `monitoring_check_history`

`MonitoringDatabaseBootstrapService` не только создаёт эти таблицы, но и умеет
мигрировать monitoring-данные из primary runtime в отдельную monitoring БД.

Вывод: как отдельный physical split этот контур теперь допустим только в явном
SQLite compatibility path. В external PostgreSQL runtime monitoring-domain уже
не должен зависеть от отдельного monitoring datasource.

### 3.4. `bot_runtime.db`

Этот файл больше не подключается в панели как отдельный live Spring datasource
контур. Он остался только как shared bot/runtime compatibility storage для
явного SQLite path.

По коду видно важный сдвиг: operator-facing сервисы панели уже не должны
считать `bot_runtime.db` canonical owner для `feedbacks` и
`client_unblock_requests`; эти таблицы читаются через основной runtime-контур,
а отдельный bot contour остаётся в compatibility/runtime-роли.

При этом есть важный архитектурный нюанс:

- `BotRuntimeContractService.buildEnvironment(...)` передаёт ботам
  `APP_DB_PANEL_RUNTIME` и `APP_DB_TICKETS`;
- `java-bot/bot-core/application.yml` задаёт
  `support-bot.database.path` через цепочку, в которой первым идёт
  `APP_DB_PANEL_RUNTIME`.

Это означает, что `java-bot` по умолчанию всё ещё тяготеет к
`panel_runtime.db`, даже если в панели уже существует отдельный
`bot_runtime.db`.

Вывод: physical datasource split для bot-runtime уже ослаблен, но transport
ownership задачи ещё не закрыты полностью, потому что сама интеграционная
модель и per-channel shard layer пока остаются.

### 3.5. `objects.db`

`objects.db` долго оставался последним реальным live split-контуром для
паспортов объектов.

Сейчас `ObjectPassportService` уже не зависит от отдельного Spring datasource
bean: в external runtime он работает через primary datasource, а локальный
SQLite datasource поднимает только лениво в compatibility path.

Доменный split при этом пока ещё остаётся логически выделенным, потому что
сам сервис и таблицы `objects` / `object_passports` ещё не растворены в общем
production storage model.

- `ObjectPassportService`

Именно этот сервис использует отдельные SQL-запросы к:

- `objects`
- `object_passports`

Вывод: physical runtime split уже ослаблен, но сам объектный контур ещё не
закрыт как архитектурная задача.

### 3.6. `clients.db`

`clients.db` создаётся и инициализируется в `DatabaseBootstrapService`.
Туда bootstrap-логика кладёт таблицы вроде:

- `clients`
- `client_usernames`
- `client_phones`
- `client_statuses`
- `client_blacklist`
- `client_unblock_requests`
- `client_avatar_history`

Но фактическая бизнес-логика панели в большинстве случаев уже не использует
этот secondary контур как основной источник истины. Те же клиентские таблицы
активно читаются из primary runtime через обычный `JdbcTemplate` и JPA.

Вывод: `clients.db` сейчас скорее transitional/legacy split, чем реальный
канонический контур.
Дополнительно это уже не live datasource contour external runtime:
отдельный SQLite datasource для него остаётся только внутри local bootstrap.

### 3.7. `knowledge_base.db`

`knowledge_base.db` также инициализируется через `DatabaseBootstrapService`,
но текущая knowledge-логика панели использует JPA-репозитории и primary
runtime DB.

Bootstrap создаёт:

- `knowledge_articles`
- `knowledge_article_files`
- `it_equipment_catalog`

Но `KnowledgeBaseService` работает через стандартные JPA-репозитории, которые
по умолчанию подключены к `panel_runtime.db`.

Вывод: `knowledge_base.db` существует физически, но knowledge-домен уже
фактически живёт в `panel_runtime.db`.
Как и `clients.db`, этот файл больше не поднимается как отдельный live
Spring datasource outside явного SQLite bootstrap path.

### 3.8. `settings.db`

`settings.db` не является общей business БД настроек в широком смысле.

Сейчас её основная роль - registry/metadata слой для bot/database wiring:

- `database_registry`
- `bot_instances`
- `database_links`

Это обеспечивает `BotDatabaseRegistry`.

Вывод: `settings.db` - служебный transitional реестр, а не полноценный
source of truth для прикладных настроек панели. В external runtime он больше
не должен выглядеть как отдельный live datasource contour.

## 4. Как БД используются в `java-bot`

В `java-bot` ситуация проще, но есть важная особенность.

Основной datasource `bot-core` строится в `DataSourceConfig`, а путь берётся из
свойства:

```yaml
support-bot:
  database:
    path: ${SUPPORT_BOT_DATABASE_PATH:${APP_DB_BOT_RUNTIME:${APP_DB_BOT:${APP_DB_PANEL_RUNTIME:${APP_DB_TICKETS:../bot_runtime.db}}}}}
```

Это значит:

1. если panel сознательно прокидывает shared SQLite business path, он идёт через `SUPPORT_BOT_DATABASE_PATH`;
2. default bot-side fallback теперь смотрит в `APP_DB_BOT_RUNTIME`;
3. legacy `APP_DB_PANEL_RUNTIME` / `APP_DB_TICKETS` остаются только дальним compatibility fallback;
4. fallback по умолчанию больше не привязывает `java-bot` к `panel_runtime.db` как implicit primary DB.

Следствие:

- `java-bot` больше не получает `panel_runtime.db` как default runtime contract;
- shared panel runtime в SQLite-совместимом сценарии теперь должен передаваться
  явно через `SUPPORT_BOT_DATABASE_PATH`;
- default bot-side datasource contract стал заметно ближе к отдельному
  transport/runtime contour.

Это нужно учитывать при любой работе по разделению panel и bot контуров.

## 5. Отдельный слой `bot-<channelId>.db`

Панель по-прежнему умеет создавать per-channel bot базы через
`BotDatabaseRegistry` в каталоге, заданном `APP_BOT_DATABASE_DIR`, но этот
shard-layer больше не должен расти автоматически.

Назначение этого слоя:

- хранить channel-local bot файлы;
- создавать `bot_users`;
- создавать `bot_chat_history`;
- регистрировать связь канала с его bot DB.

Теперь по умолчанию `app.bots.sqlite-per-channel-shard-enabled=false`, поэтому:

- per-channel `bot-<channelId>.db` не bootstrap-ятся автоматически даже в
  SQLite compatibility runtime;
- registry/link metadata для bot shard layer тоже не должны расти без явного
  opt-in;
- сам слой остаётся только как legacy compatibility механизм, а не как
  нормальная topology живой системы.

## 6. Ключевые архитектурные выводы

### Что уже реально отделено

- `panel_identity.db`
- `monitoring.db`
- `objects.db`

### Что задекларировано, но остаётся смешанным

- `bot_runtime.db` существует и остаётся compatibility-layer, но default
  runtime contract `java-bot` уже больше не должен неявно тянуться к
  `panel_runtime.db`;
- `clients.db` и `knowledge_base.db` существуют, но многие их домены уже
  фактически живут в primary runtime.

### Что является служебным transitional контуром

- `settings.db`

## 7. Практическая интерпретация для разработчика

Если задача касается:

- диалогов, сообщений, каналов, клиентских карточек, knowledge, задач,
  уведомлений - почти наверняка смотреть нужно в `panel_runtime.db`;
- пользователей, ролей и прав - в `panel_identity.db`;
- SSL/RMS/iiko monitoring - в `monitoring.db`;
- паспортов объектов - в `objects.db`;
- bot registry и связи каналов с bot shard-файлами - в `settings.db`, но этот
  слой уже должен считаться legacy opt-in;
- bot-side client/unblock/runtime хвостов - в `bot_runtime.db` и частично в
  `bot-<channelId>.db`.

## 8. Связанные документы

- [docs/database-paths.md](docs/database-paths.md) - текущая transitional карта
  env/path и logical contour.
- [docs/db/sqlite-target-topology.md](docs/db/sqlite-target-topology.md) -
  целевая архитектура SQLite-контура.
- [docs/sqlite_schema_snapshot.md](docs/sqlite_schema_snapshot.md) -
  исторический snapshot схем legacy SQLite-файлов.

## 9. Вывод

Текущее распределение БД в проекте уже не равно простой схеме
"один модуль = одна база". На практике проект использует смесь:

- канонических выделенных контуров;
- transitional secondary БД;
- legacy-compatible fallback wiring;
- channel-local shard-файлов для ботов.

Главный практический вывод такой:

- `panel_runtime.db` остаётся центральной business БД проекта;
- `panel_identity.db` и `monitoring.db` уже выделены правильно;
- `objects.db` реально используется отдельно;
- `clients.db`, `knowledge_base.db`, `settings.db` нужно трактовать как
  transitional/служебные контуры, а не как равноправные business source of
  truth;
- `bot_runtime.db` уже не поднимается как отдельный live datasource bean, но
  разделение panel и bot transport/runtime данных ещё не доведено до конца.

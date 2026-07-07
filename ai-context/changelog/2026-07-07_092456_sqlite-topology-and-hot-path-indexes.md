# 2026-07-07 09:24:56 — SQLite topology and hot-path indexes

## Связанные задачи

- `01-141` — Нормализовать архитектуру SQLite-баз: закрепить канонические домены, унифицировать datasource и закрыть hot-path индексы диалогов

## Пользовательский промпт

> бери в работу задачу 01-141.
> по возможности нужно ещё избавиться от всяческих помощников по типу /C:/Users/sushi/Git_H/tg_ref_b24_sup/spring-panel/src/main/resources/db/migration/sqlite/V1__baseline_schema.sql

## Затронутые файлы

- `ai-context/changelog/2026-07-07_092456_sqlite-topology-and-hot-path-indexes.md`
- `ai-context/rules/backend/04-sqlite-topology.md`
- `ai-context/tasks/task-details/01-141.md`
- `ai-context/tasks/task-list.md`
- `docs/database-paths.md`
- `docs/sqlite_schema_snapshot.md`
- `spring-panel/src/main/java/com/example/panel/config/AbstractSqliteDataSourceProperties.java`
- `spring-panel/src/main/java/com/example/panel/config/BotSqliteDataSourceConfiguration.java`
- `spring-panel/src/main/java/com/example/panel/config/BotSqliteDataSourceProperties.java`
- `spring-panel/src/main/java/com/example/panel/config/ClientsSqliteDataSourceProperties.java`
- `spring-panel/src/main/java/com/example/panel/config/KnowledgeSqliteDataSourceProperties.java`
- `spring-panel/src/main/java/com/example/panel/config/MonitoringSqliteDataSourceConfiguration.java`
- `spring-panel/src/main/java/com/example/panel/config/MonitoringSqliteDataSourceProperties.java`
- `spring-panel/src/main/java/com/example/panel/config/ObjectsSqliteDataSourceProperties.java`
- `spring-panel/src/main/java/com/example/panel/config/SecondarySqliteDataSourceConfiguration.java`
- `spring-panel/src/main/java/com/example/panel/config/SettingsSqliteDataSourceProperties.java`
- `spring-panel/src/main/java/com/example/panel/config/SqliteConnectionConfigSupport.java`
- `spring-panel/src/main/java/com/example/panel/config/SqliteDataSourceConfiguration.java`
- `spring-panel/src/main/java/com/example/panel/config/SqliteDataSourceProperties.java`
- `spring-panel/src/main/java/com/example/panel/config/UsersSqliteDataSourceConfiguration.java`
- `spring-panel/src/main/java/com/example/panel/config/UsersSqliteDataSourceProperties.java`
- `spring-panel/src/main/java/com/example/panel/service/BotDatabaseRegistry.java`
- `spring-panel/src/main/java/com/example/panel/service/DatabaseBootstrapService.java`
- `spring-panel/src/main/java/com/example/panel/service/ObjectPassportService.java`
- `spring-panel/src/main/java/com/example/panel/service/SqliteSchemaBootstrapSupport.java`
- `spring-panel/src/main/resources/application.yml`
- `spring-panel/src/main/resources/db/migration/sqlite/V1__baseline_schema.sql`
- `spring-panel/src/main/resources/db/migration/sqlite/V36__normalize_chat_history_hot_path_indexes.sql`

## Что сделано

- Вынесена общая логика `*SqliteDataSourceProperties` в единый `AbstractSqliteDataSourceProperties`, чтобы все SQLite-подключения использовали одинаковую нормализацию path, `journal_mode` и `busy_timeout`.
- `primary/users/bot/monitoring` datasource переведены на общий фабричный путь через `SqliteConnectionConfigSupport`, а для `clients/knowledge/objects/settings` добавлена отдельная конфигурация secondary datasource.
- Инициализация secondary SQLite-схем сведена в общий `SqliteSchemaBootstrapSupport`; `DatabaseBootstrapService`, `BotDatabaseRegistry` и `ObjectPassportService` перестали создавать ad hoc SQLite-подключения вручную.
- Primary datasource панели по умолчанию снова привязан к `tickets.db`, а не к fallback на `bot_database.db`.
- В baseline-схему и отдельную миграцию добавлены expression-индексы для hot-path запросов по `chat_history`:
  - сортировка истории/last-message по `ticket_id + timestamp + tg_message_id + id`
  - выборки по `ticket_id + sender + timestamp` для unread/overdue сценариев
- Зафиксирована реальная SQLite topology в `docs/database-paths.md` и в новом правиле `ai-context/rules/backend/04-sqlite-topology.md`, включая разделение на canonical runtime-базы и bootstrap/registry-контур.
- В `01-141` убраны абсолютные пути старого окружения и заменены на repo-relative ссылки.

## Проверки

- Успешно выполнено: `spring-panel\\mvnw.cmd -q -DskipTests compile`
- Дополнительно проверено: в `01-141`, `docs/database-paths.md`, `docs/sqlite_schema_snapshot.md` и новом правиле больше нет абсолютных ссылок на локальные пути вида `C:\\Users\\...`

## Ограничения и примечания

- Попытка запустить выборочные тесты через Maven упёрлась в уже существующие ошибки `testCompile` в unrelated media-reply тестах (`DialogQuickActionsIntegrationTest`, `DialogApiControllerWebMvcTest`, `DialogReplyServiceTest`, `DialogQuickActionServiceTest`). Эти падения не связаны с текущими SQLite-изменениями, но из-за них полноценный `mvn test` сейчас не зелёный.
- Текущая нормализация фиксирует реальную ownership-модель проекта. Она не переносит все legacy-домены из `tickets.db` в отдельные БД автоматически; для этого нужен отдельный runtime-перенос по доменам.

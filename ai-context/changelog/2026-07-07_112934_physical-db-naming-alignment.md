# 2026-07-07 11:29:34 — Physical DB naming alignment

## Связанные задачи

- `01-143` — Переименовать канонические SQLite-файлы под их архитектурный смысл и сохранить runtime-совместимость

## Пользовательский промпт

> создай таск и приступи к его выполнению, чтобы physical имя файла стало равно архитектурному смыслу

## Затронутые файлы

- `ai-context/changelog/2026-07-07_112934_physical-db-naming-alignment.md`
- `ai-context/rules/backend/04-sqlite-topology.md`
- `ai-context/tasks/task-details/01-143.md`
- `ai-context/tasks/task-list.md`
- `README.md`
- `docs/BOT_RUNTIME_CONTRACT.md`
- `docs/configuration.md`
- `docs/database-paths.md`
- `docs/db/migration-guide.md`
- `docs/db/sqlite-target-topology.md`
- `docs/environment_variables.md`
- `java-bot/bot-core/src/main/java/com/example/supportbot/config/DataSourceConfig.java`
- `java-bot/bot-core/src/main/resources/application.yml`
- `spring-panel/src/main/java/com/example/panel/config/BotSqliteDataSourceProperties.java`
- `spring-panel/src/main/java/com/example/panel/config/EnvDefaultsInitializer.java`
- `spring-panel/src/main/java/com/example/panel/config/SqliteDataSourceProperties.java`
- `spring-panel/src/main/java/com/example/panel/config/UsersSqliteDataSourceProperties.java`
- `spring-panel/src/main/java/com/example/panel/security/SecurityBootstrap.java`
- `spring-panel/src/main/java/com/example/panel/service/BotRuntimeContractService.java`
- `spring-panel/src/main/resources/application.yml`
- `spring-panel/src/main/resources/templates/error/500.html`
- `spring-panel/src/main/resources/templates/fragments/navbar.html`
- `spring-panel/src/test/java/com/example/panel/config/EnvDefaultsInitializerTest.java`
- `spring-panel/src/test/java/com/example/panel/controller/BotProcessApiControllerWebMvcTest.java`
- `spring-panel/src/test/java/com/example/panel/service/BotProcessLifecycleContractTest.java`
- `spring-panel/src/test/java/com/example/panel/service/BotProcessServiceTest.java`
- `spring-panel/src/test/java/com/example/panel/service/BotRuntimeContractServiceTest.java`
- `panel_runtime.db`
- `panel_identity.db`
- `bot_runtime.db`
- `spring-panel/panel_runtime.db`
- `spring-panel/panel_identity.db`
- `spring-panel/bot_runtime.db`
- `java-bot/panel_runtime.db`

## Что сделано

- Создана и выполнена задача `01-143` на physical rename канонических main SQLite-файлов.
- `spring-panel` переведён на canonical env keys и filenames:
  - `APP_DB_PANEL_RUNTIME` -> `panel_runtime.db`
  - `APP_DB_PANEL_IDENTITY` -> `panel_identity.db`
  - `APP_DB_BOT_RUNTIME` -> `bot_runtime.db`
- Сохранён compatibility-слой для legacy env keys `APP_DB_TICKETS`, `APP_DB_USERS`, `APP_DB_BOT`.
- `EnvDefaultsInitializer` научен резолвить и новые canonical paths, и старые legacy filenames как fallback.
- `BotRuntimeContractService` и `java-bot` обновлены под новый naming, при этом bot runtime всё ещё получает legacy alias `APP_DB_TICKETS` для плавного перехода.
- Физически переименованы репозиторные main DB-файлы в корне, внутри `spring-panel` и для `java-bot`.
- Обновлены активные docs, runtime diagnostics, backend-rule и target-topology source-of-truth под новый canonical naming.
- Добавлены и обновлены тесты на env defaults, runtime contract и bot-process lifecycle вокруг нового naming.
- Задача `01-143` переведена в статус `🟣`.

## Проверки

- Успешно выполнено: `spring-panel\\mvnw.cmd -q -DskipTests compile`
- Успешно выполнено: `java-bot\\mvnw.cmd -q -pl bot-core -am -DskipTests compile`
- Успешно выполнено: `spring-panel\\mvnw.cmd -q "-Dtest=EnvDefaultsInitializerTest,BotRuntimeContractServiceTest,BotProcessApiControllerWebMvcTest,BotProcessLifecycleContractTest,BotProcessServiceTest" test`

## Примечания

- Legacy secondary-файлы `clients.db`, `knowledge_base.db`, `objects.db`, `settings.db` в рамках этого шага не переименовывались и не мигрировались: они остаются отдельным transitional scope.
- Исторические документы и schema snapshots, описывающие старые `tickets.db` / `users.db` / `bot_database.db`, сознательно сохранены как historical references там, где это нужно для аудита или миграций.

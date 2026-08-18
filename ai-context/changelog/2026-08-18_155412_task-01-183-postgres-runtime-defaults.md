# Changelog — 2026-08-18 15:54:12

## User prompt

`давай дальше. большим пакетом`

## Context

Продолжение `01-183` после первого bootstrap-среза: нужно было убрать следующий production-drift слой и перевести сами runtime defaults в `PostgreSQL-first`, а не только first-run scripts.

## What changed

- `spring-panel/src/main/resources/application.yml`
  - default `app.datasource.mode` переведён с `auto` на `postgresql`.
- `spring-panel/src/main/resources/application-sqlite.yml`
  - SQLite-профиль теперь явно фиксирует `app.datasource.mode=sqlite`.
- `spring-panel/src/main/resources/application-postgres.yml`
  - PostgreSQL-профиль теперь явно фиксирует `app.datasource.mode=postgresql`.
- `spring-panel/src/main/resources/application-mysql.yml`
  - MySQL profile теперь явно фиксирует `app.datasource.mode=mysql`.
- `java-bot/bot-core/src/main/resources/application.yml`
  - default `support-bot.database.mode` переведён с `auto` на `postgresql`.
- `spring-panel/src/main/java/com/example/panel/config/EnvDefaultsInitializer.java`
  - автоматическая подстановка `APP_DB_*` SQLite-путей оставлена только для явного `app.datasource.mode=sqlite`;
  - для не-SQLite режима добавлен ранний выход с логированием.
- `spring-panel/src/test/java/com/example/panel/config/EnvDefaultsInitializerTest.java`
  - существующие проверки переведены на явный `sqlite` mode;
  - добавлен тест, что в `postgresql` mode SQLite defaults больше не подставляются.
- `README.md`, `docs/configuration.md`, `docs/environment_variables.md`, `docs/BOT_RUNTIME_CONTRACT.md`, `docs/POSTGRESQL_FIRST_READINESS_CLOSEOUT.md`, `docs/SQLITE_BOOTSTRAP_PERIMETER.md`, `docs/target-production-architecture-plan.md`
  - документация синхронизирована с новым PostgreSQL-first runtime contract.
- `ai-context/tasks/task-details/01-183.md`
  - добавлен апдейт по второму большому runtime-срезу.

## Validation

- `spring-panel`: `./mvnw "-Dtest=EnvDefaultsInitializerTest,ExternalDatabaseSettingsResolverTest" test`
  - результат: `BUILD SUCCESS`
- `java-bot`: `./mvnw -pl bot-core "-Dtest=ExternalDatabaseSettingsResolverTest,BotDatabaseRuntimeModeTest,DataSourceConfigTest" test`
  - результат: `BUILD SUCCESS`
- `rg -n 'mode: \${APP_DB_MODE:auto}|APP_DB_MODE:auto' spring-panel/src/main/resources java-bot/bot-core/src/main/resources README.md docs -g '*.yml' -g '*.md'`
  - результат: совпадений для runtime defaults не найдено

## Notes

- Во время `spring-panel` test/build Maven снова потрогал tracked CSS-артефакты в `spring-panel/src/main/resources/static/css/*.css`; это побочный эффект `dart-sass-maven-plugin`, а не отдельная целевая часть изменения.

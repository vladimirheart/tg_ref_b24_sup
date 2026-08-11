# 2026-08-11 16:54:20 - postgres runtime boundary

## Request
сделай задачу 01-181

## Summary
- перевёл `spring-panel` на runtime-aware DB mode, чтобы в external DB-режиме secondary/user/bot/settings datasource-бины переиспользовали primary JDBC datasource вместо открытия отдельных SQLite-файлов;
- отключил SQLite-специфичный bootstrap и file-health checks там, где panel теперь работает поверх external datasource;
- расширил runtime contract запуска ботов: панель теперь пробрасывает PostgreSQL env в `java-bot`, а SQLite-пути оставляет только для явного `APP_DB_MODE=sqlite`;
- ослабил SQLite-specific предположения в `SecurityBootstrap`, чтобы базовые identity-проверки и bootstrap жили поверх JDBC metadata/SQL, а не через `sqlite_master` и `PRAGMA`;
- синхронизировал тестовые конструкторы и документацию под новый migration boundary.

## Files Changed
- `spring-panel/src/main/java/com/example/panel/config/DatabaseMode.java`
- `spring-panel/src/main/java/com/example/panel/config/ExternalDatabaseSettings.java`
- `spring-panel/src/main/java/com/example/panel/config/PanelDatabaseRuntimeMode.java`
- `spring-panel/src/main/java/com/example/panel/config/UsersSqliteDataSourceConfiguration.java`
- `spring-panel/src/main/java/com/example/panel/config/BotSqliteDataSourceConfiguration.java`
- `spring-panel/src/main/java/com/example/panel/config/MonitoringSqliteDataSourceConfiguration.java`
- `spring-panel/src/main/java/com/example/panel/config/SecondarySqliteDataSourceConfiguration.java`
- `spring-panel/src/main/java/com/example/panel/security/SecurityBootstrap.java`
- `spring-panel/src/main/java/com/example/panel/service/BotDatabaseRegistry.java`
- `spring-panel/src/main/java/com/example/panel/service/BotRuntimeContractService.java`
- `spring-panel/src/main/java/com/example/panel/service/DatabaseBootstrapService.java`
- `spring-panel/src/main/java/com/example/panel/service/DatabaseHealthService.java`
- `spring-panel/src/main/java/com/example/panel/service/MonitoringDatabaseBootstrapService.java`
- `spring-panel/src/test/java/com/example/panel/service/BotProcessLifecycleContractTest.java`
- `spring-panel/src/test/java/com/example/panel/service/BotProcessServiceTest.java`
- `spring-panel/src/test/java/com/example/panel/service/BotRuntimeContractServiceTest.java`
- `docs/configuration.md`
- `docs/environment_variables.md`

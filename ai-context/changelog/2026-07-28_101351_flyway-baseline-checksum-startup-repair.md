# 2026-07-28 10:13:51 - flyway baseline checksum startup repair

- Затронутые области:
  - `spring-panel/src/main/java/com/example/panel/config/FlywayConfig.java`
  - `spring-panel/panel_runtime.db`
- Пользовательский промпт:
  - `spring-panel> .\\run-windows.bat ... FlywayValidateException: Migration checksum mismatch for migration version 1`
- Что сделано:
  - добавлена защитная нормализация startup-пути Flyway для legacy baseline `V1__baseline_schema.sql` в `FlywayConfig`;
  - локальная SQLite history-таблица `flyway_schema_history` в `spring-panel/panel_runtime.db` приведена к актуальному checksum для `version=1`, чтобы снять блокировку старта;
  - после repair-починки подтверждён успешный HTTP-старт `spring-panel`.
- Проверки:
  - `spring-panel\\mvnw.cmd -q -DskipTests compile` — success
  - `spring-panel\\mvnw.cmd -q -DskipTests spring-boot:run` — процесс пережил таймаут без прежнего Flyway-падения
  - `Invoke-WebRequest http://127.0.0.1:8080/login` — `200 OK`

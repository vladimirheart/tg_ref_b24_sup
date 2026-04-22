# 2026-04-22 13:26:00 — Выделение отдельной monitoring.db для SSL и RMS

## Что сделано

- Добавлен отдельный SQLite datasource `monitoring.db` для всех мониторингов панели.
- SSL- и RMS-репозитории переведены с primary JPA datasource на отдельный `monitoringJdbcTemplate`.
- Добавлен bootstrap `MonitoringDatabaseBootstrapService`, который создаёт таблицы мониторинга в `monitoring.db`.
- Реализована миграция существующих записей SSL и RMS из старой primary DB в `monitoring.db` без дублей.
- Seed-импорт RMS оставлен поверх нового monitoring storage и переведён на корректный порядок запуска.
- Добавлен `APP_DB_MONITORING` в авто-дефолты окружения.
- Локально создана и заполнена `spring-panel/monitoring.db`:
  - `ssl_certificate_monitors = 29`
  - `rms_license_monitors = 212`

## Затронутые файлы

- `ai-context/tasks/task-list.md`
- `ai-context/tasks/task-details/01-046.md`
- `spring-panel/src/main/resources/application.yml`
- `spring-panel/src/main/java/com/example/panel/config/EnvDefaultsInitializer.java`
- `spring-panel/src/main/java/com/example/panel/config/MonitoringSqliteDataSourceConfiguration.java`
- `spring-panel/src/main/java/com/example/panel/config/MonitoringSqliteDataSourceProperties.java`
- `spring-panel/src/main/java/com/example/panel/repository/SslCertificateMonitorRepository.java`
- `spring-panel/src/main/java/com/example/panel/repository/RmsLicenseMonitorRepository.java`
- `spring-panel/src/main/java/com/example/panel/service/SslCertificateMonitoringService.java`
- `spring-panel/src/main/java/com/example/panel/service/RmsLicenseMonitoringService.java`
- `spring-panel/src/main/java/com/example/panel/service/RmsMonitoringSeedImportService.java`
- `spring-panel/src/main/java/com/example/panel/service/MonitoringDatabaseBootstrapService.java`
- `spring-panel/monitoring.db`

## Проверка

- `spring-panel\\mvnw.cmd -q -DskipTests compile`
- Фактическая локальная миграция данных в `spring-panel\\monitoring.db`

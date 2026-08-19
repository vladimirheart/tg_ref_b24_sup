# 2026-08-19 10:22:30 - task-01-183 monitoring runtime alias

- User prompt:
  `давай следующий блок`
- Scope:
  - сократить live monitoring split в `spring-panel`;
  - перевести monitoring repositories на runtime-aware alias;
  - сохранить явный SQLite compatibility path без поломки bootstrap слоя.

## Что изменено

- В `MonitoringSqliteDataSourceConfiguration` добавлен бин
  `monitoringRuntimeJdbcTemplate`, который:
  - в `APP_DB_MODE=postgresql` использует primary `JdbcTemplate`;
  - в `APP_DB_MODE=sqlite` использует `monitoringJdbcTemplate`.
- `SslCertificateMonitorRepository`,
  `RmsLicenseMonitorRepository`,
  `IikoApiMonitorRepository`,
  `MonitoringCheckHistoryRepository` и
  `RmsRefreshQueueRepository`
  переведены на `monitoringRuntimeJdbcTemplate`.
- Обновлены `docs/database_distribution.md`,
  `docs/target-production-architecture-plan.md` и
  `ai-context/tasks/task-details/01-183.md`.
- Добавлен тест `MonitoringSqliteDataSourceConfigurationTest`.

## Проверка

- `./mvnw "-Dtest=MonitoringSqliteDataSourceConfigurationTest,RmsRefreshQueueRepositoryTest,IikoApiMonitoringServiceTest,RmsLicenseMonitoringServiceTest,LegacySqliteCompatibilityRunnersTest" test`
  - `BUILD SUCCESS`

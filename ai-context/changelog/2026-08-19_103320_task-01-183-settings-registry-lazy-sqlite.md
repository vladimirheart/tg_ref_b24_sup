# 2026-08-19 10:33:20 - task-01-183 settings registry lazy sqlite

- User prompt:
  `давай следующий блок, если этот завершён`
- Scope:
  - вывести `settings.db` из общего Spring datasource graph;
  - оставить registry/shard metadata только как lazy SQLite compatibility layer;
  - синхронизировать тесты и архитектурную документацию.

## Что изменено

- Из `SecondarySqliteDataSourceConfiguration` удалён бин `settingsDataSource`.
- `BotDatabaseRegistry` больше не зависит от injected `settingsDataSource` и
  лениво создаёт SQLite datasource для `settings.db` только внутри explicit
  `sqlite` compatibility path.
- `BotDatabaseRegistryTest` обновлён под новый lazy-contract.
- Обновлены `docs/database_distribution.md`,
  `docs/SQLITE_BOOTSTRAP_PERIMETER.md`,
  `docs/POSTGRESQL_FULL_PRODUCTION_GAP_AUDIT.md` и
  `ai-context/tasks/task-details/01-183.md`.

## Проверка

- `./mvnw clean test "-Dtest=BotDatabaseRegistryTest,ClientsServiceTest,BotRuntimeBlacklistServiceTest,ClientProfileApiControllerTest,MonitoringSqliteDataSourceConfigurationTest,LegacySqliteCompatibilityRunnersTest"`
  - `BUILD SUCCESS`

# 2026-08-19 11:05:40 - task-01-183 clients knowledge lazy bootstrap

- User prompt:
  `юери следующий пакет.`
  `что в целом осталось по задаче?`
- Scope:
  - вывести `clients.db` и `knowledge_base.db` из общего Spring datasource graph;
  - оставить их только как lazy SQLite bootstrap contours;
  - обновить документацию и зафиксировать статус remaining work.

## Что изменено

- Из `SecondarySqliteDataSourceConfiguration` удалены `clientsDataSource` и
  `knowledgeDataSource`.
- `DatabaseBootstrapService` теперь сам лениво создаёт SQLite datasource для
  `clients.db` и `knowledge_base.db` только при явном `sqlite` runtime.
- Обновлены `docs/database_distribution.md`,
  `docs/POSTGRESQL_FULL_PRODUCTION_GAP_AUDIT.md` и
  `ai-context/tasks/task-details/01-183.md`.

## Проверка

- `./mvnw "-Dtest=BotDatabaseRegistryTest,ClientsServiceTest,BotRuntimeBlacklistServiceTest,ClientProfileApiControllerTest,MonitoringSqliteDataSourceConfigurationTest,LegacySqliteCompatibilityRunnersTest" test`
  - `BUILD SUCCESS`

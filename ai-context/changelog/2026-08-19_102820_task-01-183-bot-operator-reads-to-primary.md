# 2026-08-19 10:28:20 - task-01-183 bot operator reads to primary

- User prompt:
  `давай следующий блок, если этот завершён`
- Scope:
  - убрать operator-facing зависимости `spring-panel` от `botJdbcTemplate`
    там, где ownership уже фактически у основного runtime-контура;
  - перевести `feedbacks` и `client_unblock_requests` на canonical path;
  - синхронизировать тесты и архитектурную документацию.

## Что изменено

- `ClientsService` больше не читает `feedbacks` через `botJdbcTemplate`.
- `UnblockRequestService` и `BotRuntimeBlacklistService` больше не работают с
  `client_unblock_requests` через `botJdbcTemplate`; используется основной
  `JdbcTemplate`.
- `ClientProfileApiController` больше не держит прямую live-зависимость от
  `botJdbcTemplate`: после optional SQLite compatibility probe fallback идёт
  через основной runtime-контур.
- Обновлены `ClientProfileApiControllerTest`,
  `BotRuntimeBlacklistServiceTest`,
  `ClientsServiceTest`.
- Обновлены `docs/database_distribution.md`,
  `docs/target-production-architecture-plan.md` и
  `ai-context/tasks/task-details/01-183.md`.

## Проверка

- `./mvnw clean test "-Dtest=ClientsServiceTest,BotRuntimeBlacklistServiceTest,ClientProfileApiControllerTest,MonitoringSqliteDataSourceConfigurationTest,LegacySqliteCompatibilityRunnersTest"`
  - `BUILD SUCCESS`

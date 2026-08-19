# 2026-08-19 10:14:30 - task-01-183 legacy import opt-in

- User prompt:
  `продолжай.`
  `что в целом ещё осталось?`
- Scope:
  - перевести legacy SQLite import/recovery в explicit compatibility flow;
  - убрать runtime marker-table bootstrap из PostgreSQL import runner;
  - зафиксировать новый контракт тестами и документацией.

## Что изменено

- Добавлен `spring-panel/src/main/java/com/example/panel/config/LegacySqliteCompatibilitySettings.java`.
- `LegacySqliteImportService`, `PostgresLegacyCriticalDataRecoveryService` и
  `PostgresImportedDataReconciliationService` теперь по умолчанию не выполняют
  legacy SQLite import/recovery в обычном PostgreSQL runtime.
- `LegacySqliteImportService` больше не создаёт `legacy_sqlite_imports`
  самостоятельно и опирается на Flyway-owned схему.
- Добавлен unit-test `LegacySqliteCompatibilityRunnersTest`.
- Обновлены `.env.example`, `docs/SQLITE_BOOTSTRAP_PERIMETER.md`,
  `docs/POSTGRESQL_FULL_PRODUCTION_GAP_AUDIT.md` и
  `ai-context/tasks/task-details/01-183.md`.

## Проверка

- `./mvnw "-Dtest=LegacySqliteCompatibilityRunnersTest,ChatAttachmentMetadataAvailabilityServiceTest,BotRuntimeTicketWriteServiceTest" test`
  - `BUILD SUCCESS`

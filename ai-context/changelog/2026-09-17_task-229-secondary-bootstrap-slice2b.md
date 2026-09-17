# 01-229 — secondary/bootstrap structural slice 2B

## Что изменено

- Удалены unreachable panel SQLite bootstrap runners: `DatabaseBootstrapService` и `MonitoringDatabaseBootstrapService`.
- Удалены secondary compatibility properties для `clients.db`, `knowledge_base.db` и `objects.db`, а также `SecondarySqliteDataSourceConfiguration`, `SqliteSchemaBootstrapSupport` и `SqliteConnectionConfigSupport`.
- `ObjectPassportService` больше не умеет лениво открывать `objects.db` и всегда использует canonical primary datasource.
- Monitoring wiring переименован в `MonitoringDataSourceConfiguration`: monitoring datasource/JDBC остаются aliases canonical primary datasource.
- Оставшиеся legacy source-path properties для primary/monitoring/bot собраны в `LegacySqliteArchiveConfiguration`; эта конфигурация не создаёт runtime `DataSource` и нужна только explicit archive/recovery contract.
- Из normal `application.yml` удалены dead users/clients/knowledge/objects SQLite path mappings; primary/monitoring/bot mappings явно помечены archive/recovery-only.
- Current-state storage/startup docs синхронизированы; source-contract startup ownership больше не требует удалённые compatibility runner-ы.

## Проверка

Guarded operator выполняет в detached sandbox:

- exact baseline/blob guards;
- semantic scan active main/test graph на удалённые compatibility identifiers;
- `spring-panel` test compilation;
- targeted tests для archive properties boundary, monitoring aliases, object passports и legacy import runners;
- deterministic контроль Maven-generated CSS drift с Git-native restore до final changed-file gate;
- `git diff --check`;
- exact changed-file gate.

## Safety

- staging/commit/push operator не выполняет;
- deployment/restart не выполняется;
- DB migration/backfill/import не запускаются;
- NetBox/external systems не изменяются;
- backend-owned legacy import/recovery services и bot worker technical SQLite store сохраняются.

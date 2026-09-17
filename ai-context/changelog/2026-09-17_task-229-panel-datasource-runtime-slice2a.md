# 01-229 — panel datasource/runtime structural slice 2A

## Что изменено

- Primary datasource configuration переименован из `SqliteDataSourceConfiguration` в `PanelDataSourceConfiguration`.
- Primary datasource больше не принимает `SqliteDataSourceProperties` как фиктивную runtime dependency и создаётся только из external datasource contract.
- `usersJdbcTemplate` перенесён в `UsersDataSourceConfiguration` и остаётся alias на canonical primary datasource без `UsersSqliteDataSourceProperties`.
- `UsersSqliteDataSourceProperties` удалён из active source graph.
- `DatabaseHealthService` больше не ветвится на SQLite-файлы и проверяет только canonical external runtime/identity tables.
- `docs/database_distribution.md` переписан как current PostgreSQL production ownership reference; legacy SQLite явно отделён как import/recovery/test perimeter.
- `01-229` остаётся в работе: bootstrap/secondary compatibility graph и оставшиеся `isSqliteMode()` branches удаляются следующими slices.

## Проверка

Guarded operator выполняет в detached sandbox:

- exact baseline/blob guards;
- `spring-panel` test compilation;
- targeted tests `PanelDataSourceConfigurationTest`, `UsersDataSourceConfigurationTest`, `DatabaseHealthServiceExternalModeTest`, `ProductionDatabaseModeContractTest`;
- контроль Maven-generated CSS drift с Git-native restore до final changed-file gate;
- `git diff --check`;
- exact changed-file gate.

## Safety

- staging/commit/push operator не выполняет;
- deployment/restart не выполняется;
- DB migration/backfill/import не запускаются;
- NetBox/external systems не изменяются;
- archive legacy SQLite import/recovery tooling не удаляется этим slice.

# PostgreSQL schema and migration guide

The project has one supported relational database: PostgreSQL. The canonical Flyway chain is:

`spring-panel/src/main/resources/db/migration/postgresql`

## Runtime contract

Normal runtime uses `APP_DB_MODE=postgresql` together with the canonical `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` and `SPRING_DATASOURCE_PASSWORD` values. `spring-panel`, `java-bot` and dynamic bot children do not select an alternative SQL vendor at runtime.

## Adding a schema change

1. Add the next ordered migration under `spring-panel/src/main/resources/db/migration/postgresql`.
2. Keep the migration valid for PostgreSQL; do not add parallel SQLite, MySQL or H2 migration chains.
3. Keep schema ownership backend/Flyway-owned. Bot processes must not create or mutate the canonical business schema.
4. Add or update PostgreSQL-compatible tests for the changed contract.
5. Run Flyway validation and the relevant acceptance tests before rollout.

## Legacy database artifacts

Legacy SQLite/MySQL/H2 references can remain only as explicit historical evidence, test fixtures or negative-contract assertions. The former SQLite exporter, Alembic compatibility workflow and MySQL-target commands are not a supported operational migration path in the current repository.

If historical data ever requires a new recovery/import procedure, design it as a separate reviewed one-off PostgreSQL migration project. Do not re-enable a legacy runtime mode or restore retired migration tooling as part of normal startup.

## Verification

A valid change must leave PostgreSQL as the only live/selectable SQL backend, keep the PostgreSQL Flyway chain authoritative, and pass the targeted PostgreSQL acceptance contour.

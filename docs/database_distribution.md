# Распределение БД в проекте Iguana

## Назначение документа

Этот документ фиксирует текущую PostgreSQL-only SQL topology после задачи `01-277`.

## Production runtime

PostgreSQL — единственная SQL/реляционная БД для `spring-panel`, `java-bot` и dynamic bot children. Canonical datasource contract задаётся через:

- `APP_DB_MODE=postgresql`;
- `SPRING_DATASOURCE_URL`;
- `SPRING_DATASOURCE_USERNAME`;
- `SPRING_DATASOURCE_PASSWORD`.

`spring-panel` владеет Flyway schema lifecycle. Bot runtimes получают готовый PostgreSQL datasource contract и не выполняют schema bootstrap/migration.

## Canonical ownership

| Контур | Production owner | Runtime access |
| --- | --- | --- |
| panel/business runtime | PostgreSQL | primary JPA + primary `JdbcTemplate` |
| identity/auth | PostgreSQL | alias canonical primary datasource |
| monitoring/read models | PostgreSQL | canonical runtime JDBC |
| bot runtime | PostgreSQL | canonical datasource + queue/internal API boundary |
| object passports | PostgreSQL | canonical panel datasource |

Redis, RabbitMQ и MinIO/S3 остаются отдельными infrastructure boundaries и не являются SQL-БД.

## Cleanup status

В рамках `01-277` удалены альтернативные SQL runtime modes, legacy SQL import/recovery tooling, non-PostgreSQL migration chains и SQL test fixtures. Historical changelog/task records могут описывать прежнюю topology, но не являются runtime contract.

## Operational rule

Для production-проверки ориентируйтесь на фактический PostgreSQL datasource contract, readiness probes и container/process env. Любой selectable non-PostgreSQL SQL path считается regression.

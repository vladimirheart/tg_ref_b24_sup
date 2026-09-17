# 01-229 — panel runtime SQLite branches structural slice 2C

## Что изменено

- Удалены `DatabaseMode.SQLITE`, `PanelDatabaseRuntimeMode.isSqliteMode()` и `isExternalDatabaseEnabled()` из production spring-panel graph.
- `PanelTimestampSqlSupport` теперь содержит только external runtime SQL; SQLite dialect вынесен в test-only `SqlitePanelTimestampSqlSupport`.
- Security bootstrap больше не создаёт `user_authorities` в runtime и не разрешает dev fallback `admin/admin`; schema остаётся Flyway-owned.
- Security runtime secrets проверяются без SQLite bypass.
- Dialog participant/read paths очищены от SQLite boolean/timestamp/schema branches.
- Monitoring history retention всегда использует shared lease.
- Child bot runtime contract больше не зависит от panel/bot SQLite path property holders; PostgreSQL-only child-bot datasource contract сохранён.
- Удалён retired `APP_SECURITY_BOOTSTRAP_ADMIN_ALLOW_DEFAULT_CREDENTIALS_IN_SQLITE` из normal config/reference.

## Что сохранено намеренно

- explicit legacy SQLite archive/import/recovery services и их property holders;
- historical Flyway schema-history identifiers с `sqlite` в имени;
- SQLite fixtures в `spring-panel/src/test`;
- `java-bot` technical worker/local SQLite perimeter.

## Проверка

Guarded operator выполняет на detached baseline sandbox:

- exact HEAD/origin-main/content guards;
- deterministic source transform;
- `spring-panel` test compilation;
- targeted runtime/security/dialog/bot contract tests;
- semantic absence gates для retired panel runtime identifiers;
- deterministic cleanup Maven-generated CSS side effects;
- `git diff --check`;
- exact changed-file gate.

## Safety

- operator не выполняет staging/commit/push;
- deployment/restart не выполняются;
- DB migration/backfill/import не запускаются;
- NetBox/external systems не изменяются.

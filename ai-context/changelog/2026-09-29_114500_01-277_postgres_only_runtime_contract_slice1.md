# 01-277 — PostgreSQL-only runtime contract, slice 1

Время: 2026-09-29 11:45 +03:00

## Пользовательский запрос

`проект должен работать только на postgres. остальные бесполезные. если это не так и требуется держать кроме postgres - напиши`

Продолжение после подтверждения целевого контракта:

`погнали`

## Что меняется этим срезом

- java-bot datasource contract становится PostgreSQL-only; SQLite и worker DB modes отклоняются fail-fast;
- удаляются java-bot SQLite schema/trigger initializers и schema-sqlite.sql;
- из java-bot Maven dependency graph удаляются sqlite-jdbc и hibernate-community-dialects;
- spring-panel DatabaseMode/ExternalDatabaseSettingsResolver/Flyway location становятся PostgreSQL-only;
- удаляется application-mysql.yml;
- из spring-panel Maven dependency graph удаляются sqlite-jdbc, flyway-mysql, mysql-connector-j, H2 и hibernate-community-dialects;
- targeted tests переписываются на новый PostgreSQL-only runtime contract;
- legacy migration/import/test fixtures ещё не удаляются этим срезом и будут закрыты последующими slices 01-277.

## Safety

- runtime, DB, queue, env, images и containers не изменяются;
- stage/commit/push не выполняются;
- Maven запускается только для compile/test validation source-state.

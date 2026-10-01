# Retired SQLite perimeter after PostgreSQL-only cutover

This path is retained as a navigation anchor for older task/changelog links. It is not a live bootstrap guide.

## Current relational-storage contract

- Canonical business/runtime SQL storage is PostgreSQL.
- `spring-panel`, `java-bot` and dynamic bot children do not create or select a SQLite datasource.
- There is no `APP_DB_MODE=worker` database mode and no temporary bot-worker SQLite store.
- There is no first-party legacy SQLite import/recovery bootstrap in the normal repository workflow.

## Residual references that may remain

SQLite references are acceptable only when they are clearly one of these:

- historical task/changelog or incident evidence;
- historical schema/log snapshots;
- test fixtures that do not create a live runtime datasource;
- negative-contract tests proving that non-PostgreSQL modes are rejected.

Legacy `*.db` files, if retained for audit/history, are evidence only. Their presence must never change startup behavior or become a fallback datasource.

For the current production ownership model use `database_distribution.md` and the production runbooks.

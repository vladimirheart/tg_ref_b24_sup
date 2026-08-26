# 01-211 Phase B — distributed UI fanout and migration ownership

Date: 2026-08-26 10:22 +03:00
Task: 01-211

## User prompt

«репо обновил.
вывод работы скрипта:»

К сообщению приложен успешный вывод `apply-01-211-phase-a-runtime-roles.ps1`: 34 background source-файла классифицированы, targeted tests и `git diff --check` прошли.

## Changes

- Added Redis pub/sub fanout for UI events so worker-side durable work can notify SSE clients on any web replica.
- Kept process-local fallback only for compatibility `all` mode; split `web`/`worker` roles require explicit Redis fanout.
- Added `migrate/db-migrate` runtime role and optional exit-after-migration behavior.
- Made Flyway migration ownership explicit: `all`/`migrator` migrate; `web`/`worker` do not.
- Classified panel ApplicationRunner startup mutations by runtime ownership.
- Added startup ownership and YAML parse contracts.
- Fixed malformed Phase A metrics YAML where `instance_id` and `distribution` were accidentally joined.

## Status

Task 01-211 remains `🟡`. Production compose is intentionally not split until side-effecting `@PostConstruct` hooks and remaining singleton worker workloads are audited/hardened.

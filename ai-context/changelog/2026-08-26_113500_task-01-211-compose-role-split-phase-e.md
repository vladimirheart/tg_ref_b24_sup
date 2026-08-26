# 01-211 Phase E — production Compose deployment-role split

Date: 2026-08-26 11:35 +03:00
Task: 01-211

## User prompt

«репо обновлён
вывод работы скрипта прикладываю»

Приложенный вывод подтвердил:
- Phase D/D1 `test-compile` OK;
- Phase D targeted tests OK;
- `git diff --check` OK;
- `01-211 Phase D verification is GREEN`.

## Changes

- Replaced legacy `spring-panel` Compose service with one-shot `db-migrate`, scalable `panel-web`, scalable `ops-worker`.
- Added stable loopback `panel-direct` nginx so web replicas do not bind host `8080`.
- Removed hardcoded `container_name` entries from production contour.
- Routed public nginx and bot internal API only to `panel-web`.
- Added dynamic nginx DNS upstream and next-upstream failover for scaled web replicas.
- Added panel container entrypoint with hostname instance id and per-replica log path.
- Added replica controls to PowerShell/Bash production up helpers.
- Added split-role master-key preflight.
- Added orphan cleanup to up/down helpers for migration from old service names.
- Updated env template and production/edge/runtime runbooks.
- Added Docker topology source-contract test.
- Added isolated real Docker role/scale smoke script for 2 web + 2 worker replicas.

## Status

Task remains `🟡` until the real Docker role/scale smoke passes.

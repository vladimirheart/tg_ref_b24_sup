# 01-211 Phase D — durable backend ops command boundary

Date: 2026-08-26 12:05 +03:00
Task: 01-211

## User input

User provided successful Phase C1 verification output:
- spring-panel test-compile OK;
- all Phase C targeted tests OK;
- git diff --check OK.

## Why

Four shared backend services still accepted operator/scheduler requests and executed long-running work in process-local executors. A physical `panel-web` / `ops-worker` split would therefore leave expensive external integrations running inside web JVMs.

## Changes

- Added migration-owned `backend_ops_command` ledger for PostgreSQL and SQLite.
- Added exclusive active command key, atomic database claim, stale recovery, heartbeat/progress/result/error lifecycle.
- Added `DATABASE_CLAIMED` runtime replica policy.
- Added worker-only backend ops dispatcher.
- Routed explicit-role RMS license/network refresh through durable commands.
- Routed explicit-role iiko API refresh through durable commands.
- Routed explicit-role iiko locations sync through durable commands.
- Routed explicit-role NetBox passport sync through durable commands.
- Kept legacy local executors only for `APP_RUNTIME_ROLE=all` compatibility.
- Mapped status endpoints back to existing DTO shapes.
- Added command service tests and boundary source-contract tests.

## Status

01-211 remains `🟡`. The next slice is production Docker Compose split plus role/scale smoke.

## Verification recovery D1

The first complete Phase D test run compiled successfully and passed 36/37 tests.

The only failure was a stale Phase C source-contract assertion:
`RuntimeLifecycleBoundarySourceContractTest` still required `RuntimeRole.WORKER` inside
`RmsLicenseMonitoringService.restoreQueuedRefreshTasks()`.

That expectation is no longer correct after Phase D. Explicit `web|worker` roles now use
`backend_ops_command`; the legacy `rms_refresh_queue` restore path is deliberately compatibility-only
for `RuntimeRole.ALL`.

Recovery D1 updates the contract test to assert:
- `ApplicationReadyEvent`;
- `RuntimeRole.ALL`;
- the explicit `backend_ops_command` ownership message.

No production behavior is relaxed or reverted.

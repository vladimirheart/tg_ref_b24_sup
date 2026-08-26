# Async backend command boundary — pre-compose audit

Date: 2026-08-26
Task: 01-211
Status: Phase C blocker inventory.

## Rule

`panel-web` may validate and enqueue an operator command, but a long-running external integration operation must not execute in the web process after the production role split.

The durable owner is `ops-worker`.

## Current process-local executor blockers

### RMS monitoring

`RmsLicenseMonitoringService` has persisted `rms_refresh_queue`, which is a strong starting point, but request methods both persist the command and submit a process-local executor immediately. `ApplicationReadyEvent` also restores queued work.

Target:
- web: enqueue only;
- worker: claim and execute persisted queue entries;
- worker restart: resume safely;
- duplicate workers: claim-safe;
- UI status: derived from durable state, not only process memory.

### iiko API monitoring

`IikoApiMonitoringService` uses a process-local single-thread executor and process-local pending/running state.

Target: durable ops command + worker dispatcher.

### iiko department/location sync

`IikoDepartmentLocationsSyncService` uses a process-local executor and in-memory progress snapshot.

Target: durable command and durable progress/result snapshot.

### NetBox object passport sync

`NetBoxObjectPassportSyncService` uses a process-local executor and in-memory progress snapshot.

Target: durable command and durable progress/result snapshot.

## Recommended common contract

Do not build four unrelated internal HTTP services.

Introduce one backend-owned command contract, for example:

```text
backend_ops_command
- command_id
- command_type
- payload_json
- status
- requested_by
- requested_at
- available_at
- claimed_by
- claimed_at
- heartbeat_at
- completed_at
- result_json
- last_error
- attempt_count
```

with:
- atomic claim;
- stale claim recovery;
- idempotency key where operator retries are possible;
- worker polling/dispatch;
- role-safe status API;
- optional Redis UI event fanout when state changes.

This is not a new microservice. It is a durable boundary inside the canonical backend that later allows a dedicated integration worker/service to be extracted without changing operator APIs.

## Phase D implementation

The target boundary is now implemented by `backend_ops_command`.

- PostgreSQL: `V40__backend_ops_command.sql`.
- SQLite: `V51__backend_ops_command.sql`.
- one nullable unique `active_key` per command type prevents duplicate active execution across replicas;
- worker claim uses compare-and-set `UPDATE ... WHERE status='queued'`;
- stale running claims are returned to the queue;
- progress and terminal result/error are durable;
- `panel-web` only enqueues in explicit role mode;
- `ops-worker` executes through `BackendOpsCommandDispatcher`;
- legacy process-local executors remain only as `APP_RUNTIME_ROLE=all` compatibility behavior.

Covered command types:

- `rms.license.refresh`;
- `rms.network.refresh`;
- `iiko.api.refresh`;
- `iiko.locations.sync`;
- `netbox.passports.sync`.

The next architecture step is no longer another service boundary. It is the production Compose deployment split and real Docker scale smoke.

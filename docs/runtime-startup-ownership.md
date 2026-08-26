# Runtime startup ownership

Date: 2026-08-26
Task: 01-211
Status: Phase B ownership contract; compose split not enabled yet.

## Why this exists

`panel-web x N` and `ops-worker x M` cannot safely share implicit startup mutation ownership.
Flyway, legacy import, repair/seed runners and compatibility bootstrap must have an explicit owner before the backend is scaled.

## Role semantics

- `all` — local/dev compatibility. Every role-classified workload remains available.
- `web` / `panel-web` — public/operator backend role.
- `worker` / `ops-worker` — background/backend consumer role.
- `migrate` / `db-migrate` — startup migration/repair/seed owner.
- `roles = {}` on `@RuntimeWorkload` means compatibility-only: enabled by `all`, disabled by explicit production roles.

## Flyway contract

- `all`: Flyway normalization + migrate runs, preserving current local behavior.
- `migrator`: Flyway normalization + migrate runs.
- `web`: Flyway strategy deliberately skips migration.
- `worker`: Flyway strategy deliberately skips migration.

Therefore production compose must start a successful migrator before web/worker.

## Current ApplicationRunner ownership

Migrator-owned:
- `LegacySqliteImportService`
- `PostgresImportedDataReconciliationService`
- `PostgresLegacyCriticalDataRecoveryService`
- `LegacyBotShardConsolidationService`
- `LegacyMonitoringHistoryCompactionService`
- `RmsMonitoringSeedImportService`
- `LocationsSharedConfigRepairService`
- security bootstrap bean from `PanelApplication`

Compatibility-only:
- `DatabaseBootstrapService`
- `MonitoringDatabaseBootstrapService`
- legacy local additional-services process check from `PanelApplication`

Worker-owned:
- scheduled services that already implement `ApplicationRunner` and carry worker `@RuntimeWorkload`.

Always/process diagnostics:
- `RuntimeRoleDiagnostics`
- `RuntimeRoleSafetyValidator`

## UI event fanout contract

Workers do not hold SSE clients. `UiEventStreamService` now publishes business/UI change events to a Redis channel.
Every web replica subscribes to that channel and delivers the event to its process-local SSE emitters.

This enables:

```text
ops-worker ---- publish ----> Redis UI channel
                                |       |
                                v       v
                           panel-web  panel-web
                              |          |
                             SSE        SSE
```

Split roles require `APP_UI_EVENT_FANOUT_MODE=redis`.
`AUTO`/`LOCAL` remains compatibility-only to avoid turning a local developer Redis outage into a hard application failure.

## Remaining startup audit before compose split

`ApplicationRunner` ownership is now guarded automatically.
The next slice must audit side-effecting `@PostConstruct`, `InitializingBean`, lifecycle/startup listeners and any manual threads that can mutate shared state.

Not every `@PostConstruct` is a background workload: dependency initialization and process-local caches are valid on multiple roles.
The audit must distinguish initialization from shared side effects rather than mechanically moving every hook to worker.

## Current @PostConstruct audit inventory

These hooks are **inventory only** in Phase B. They are not automatically moved to worker/migrator because many are process-local initialization.

- `observability/ProductionReadinessObservationCache.java`
- `security/PanelSecurityRuntimeGuard.java`
- `service/ChatAttachmentMetadataAvailabilityService.java`
- `service/MonitoringCredentialsCryptoService.java`
- `service/OperatorNotificationWatcher.java`
- `service/SidebarStatusWatcher.java`
- `service/UiEventOutboxWatcher.java`

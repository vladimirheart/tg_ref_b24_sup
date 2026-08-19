# Task 01-183 - transport retry semantics and worker checkpoints

## What changed

- Hardened `integration_inbound_event_inbox` for at-least-once delivery:
  - added retry metadata columns `attempt_count`, `processing_started_at`, `updated_at`;
  - changed inbox claim flow from one-shot insert to reclaimable processing model;
  - failed events can now be retried, and stale `processing` records can be reclaimed after timeout.
- Added canonical `runtime_worker_checkpoints` table and new `RuntimeWorkerCheckpointService`.
- Moved watcher cursors from process-local memory bootstrap only to persistent DB checkpoints:
  - `UiEventOutboxWatcher`;
  - `OperatorNotificationWatcher`.
- Added Redis lease coordination to remaining business-side scheduled mutators:
  - `DialogAutoCloseSchedulerService`;
  - `BotRuntimeBlacklistService`;
  - `OperatorNotificationWatcher`.
- Fixed PostgreSQL readiness transport probes:
  - corrected actual inbox column names;
  - added checkpoint table probe.
- Added focused regression coverage for the new inbox retry/reclaim semantics.
- Fixed missing `StringUtils` import in `AvatarService`, which blocked `spring-panel` compilation.

## Why

- Previous inbox behavior could permanently drop a message after a crash between insert and final status update.
- Previous watcher cursors were process-local and could skip events after failover/restart in a multi-instance contour.
- Some scheduled business flows still had duplicate side-effect risk under multi-instance deployment.

## Verification

- `spring-panel`: `./mvnw.cmd -q -DskipTests compile`
- `spring-panel`: `./mvnw.cmd -q "-Dtest=IntegrationInboundEventInboxServiceTest,DialogAutoCloseSchedulerServiceTest,BotRuntimeBlacklistServiceTest,OperatorNotificationWatcherTest,InboundClientMessageIngestionServiceTest,ConversationTicketCreationIngestionServiceTest,PostgresRuntimeReadinessVerifierTest" test`

## Remaining follow-up

- Finish broader transport hardening on producer side if we want durable publish semantics beyond consumer-side retry/idempotency.
- Continue reviewing remaining non-leased schedulers/watchers and split harmless local cache jobs from real multi-instance side effects.
- Revisit legacy attachment-by-path fallback endpoints and any still-live local storage compatibility paths if full canonical object-storage-only operation is required.

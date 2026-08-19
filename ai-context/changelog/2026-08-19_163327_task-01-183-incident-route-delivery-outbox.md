# Task 01-183 - incident route delivery outbox

Date: 2026-08-19 16:33:27
Task: 01-183
Initiated by: user request `хорошо, давай дальше. и напиши что осталось. да, и не забудь обновить задачу`

## Completed

- Added canonical incident route delivery contour in `spring-panel`:
  - new durable table `incident_route_delivery_outbox`;
  - PostgreSQL / SQLite / MySQL Flyway migrations;
  - PostgreSQL readiness verifier now probes the new incident-route delivery table.
- Added `IncidentRouteDeliveryService` for backend-owned route execution:
  - `webhook`;
  - `user`;
  - `users`;
  - `department`;
  - `all_operators`.
- Added `IncidentRouteDeliveryOutboxService` with:
  - queued/processing/delivered/failed lifecycle;
  - retry/backoff semantics;
  - stale-processing recovery;
  - leased scheduled dispatch for multi-instance runtime.
- Extended `IncidentService` so incident routes are no longer metadata-only:
  - create/update/event/signal flows now enqueue real route deliveries;
  - route add/update triggers immediate route delivery enqueue;
  - manual route redelivery APIs are now available for single route and failed-route batches.
- Incident API payloads now include latest delivery snapshots per route:
  - delivery status;
  - attempt count;
  - last error;
  - updated/delivered timestamps.
- Tightened incident route contract:
  - normalized supported route types;
  - prevented arbitrary route-type values from entering canonical incident runtime.
- Synced task and gap-audit docs with the new incident delivery state:
  - `ai-context/tasks/task-details/01-183.md`;
  - `docs/POSTGRESQL_FULL_PRODUCTION_GAP_AUDIT.md`.

## Verification

- `spring-panel`: `./mvnw.cmd -q -DskipTests compile`

## Notes

- Focused tests for the new route-delivery layer were added/updated, but repository-wide `spring-panel` test execution is still not a reliable package-level signal because unrelated pre-existing test-compile failures remain elsewhere in the repo.
- Remaining `01-183` scope after this package is now mostly higher-level operational/product maturity:
  - fuller incident UI/workbench;
  - deeper integration-worker replay/compensation/debug surface;
  - final multi-instance side-effect audit and documentation/runbook closeout.

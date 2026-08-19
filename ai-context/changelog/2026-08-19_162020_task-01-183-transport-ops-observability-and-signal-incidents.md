# Task 01-183 - transport ops observability and signal incidents

Date: 2026-08-19 16:20:20
Task: 01-183
Initiated by: user request `тогда забирай оба:
deeper coordination/replay/operability вокруг integration workers, если хотим ещё сильнее дожать transport beyond current outbox + consumer claim semantics;
более широкий infra/incidents/observability хвост, если он ещё не закрыт соседними пакетами.

и обнови задачу что там по текущему состоянию`

## Completed

- Added backend-owned transport operations surface in `spring-panel`:
  - new `IntegrationTransportOpsService`;
  - new analytics/controller API `/api/analytics/integration-transport`;
  - overview of `integration_inbound_event_inbox`, `integration_transport_outbox`, `runtime_worker_checkpoints`, and transport incidents.
- Added manual operability actions for transport degradation:
  - replay single failed/stale inbound event;
  - replay failed/stale inbound batch;
  - requeue single failed/stale outbound publish event;
  - requeue failed/stale outbound publish batch.
- Added automatic signal-incident monitoring for transport contour:
  - new `IntegrationTransportIncidentMonitor`;
  - runs under lease through `RuntimeCoordinationService`;
  - opens, refreshes, and resolves canonical incident `integration_transport / panel-rabbitmq-bridge` depending on health snapshot.
- Extended `IncidentService` / `IncidentRepository` with internal signal-incident workflow:
  - list incident summaries by `signal_type`;
  - open or refresh signal incidents;
  - resolve signal incidents;
  - append internal signal events.
- Added operator-facing analytics UI block `Integration Transport Ops` with:
  - live counters for failed/stale/backlog transport state;
  - tables for replayable inbox/outbox items;
  - transport incident list;
  - runtime worker checkpoint list.
- Synced task and gap-audit documentation with the actual state of `01-183`:
  - `ai-context/tasks/task-details/01-183.md`;
  - `docs/POSTGRESQL_FULL_PRODUCTION_GAP_AUDIT.md`.

## Verification

- `spring-panel`: `./mvnw.cmd -q -DskipTests compile`

## Notes

- `IntegrationTransportOpsService` was finalized without PostgreSQL-only interval SQL so the transport ops queries stay runtime-compatible.
- Focused tests for the new transport ops/controller layer were added, but repository-wide `spring-panel` test compilation still has unrelated pre-existing failures in other test classes, so `test` is not yet a reliable green signal for this package.
- Current remaining `01-183` scope is now above the base architecture layer:
  - deeper integration-worker replay/compensation tooling beyond panel inbox/outbox;
  - richer incident lifecycle UI and external alert/runbook delivery;
  - final audit of any remaining multi-instance side effects outside already leased/claimed runtime paths.

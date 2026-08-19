# Task 01-183 - incident workbench and deeper transport recovery

Date: 2026-08-19 18:34:26
Task: 01-183
Initiated by: user request `сделай:
полноценный operator-facing incident UI/workbench, а не только API/analytics/delivery слой;
ещё более глубокий replay/compensation/debug surface вокруг integration workers за пределами panel-side transport и incident route delivery;`

## Completed

- Added a dedicated operator-facing incident page in `spring-panel`:
  - new `/incidents` page controller;
  - new sidebar navigation entry;
  - new Thymeleaf template `incidents/index.html`;
  - new runtime client `incidents-workbench.js`.
- Added practical incident workbench UX on top of the existing canonical backend domain:
  - incident list with search and filters by `status`, `severity`, `signal_type`;
  - detail workbench for editing incident core fields;
  - add event/runbook note actions;
  - watcher management;
  - route management;
  - single-route and failed-route-batch redelivery actions.
- Extended incident API/service contract for operator workbench flows:
  - `IncidentApiController` now supports `query` and `signal_type` filters;
  - `IncidentService.listIncidents(...)` now supports practical text search and signal-type filtering.
- Deepened integration recovery/debug surface beyond the previous analytics-only slice:
  - transport payload detail endpoints for inbound and outbound events;
  - targeted inbound replay by `ticket_id`;
  - targeted outbound requeue by `ticket_id`;
  - manual runtime checkpoint override endpoint.
- Extended transport operations service for operator workbench usage:
  - richer event detail loading;
  - ticket-scoped compensation actions;
  - checkpoint mutation contract through `RuntimeWorkerCheckpointService`.
- Extended checkpoint service itself with generic text-cursor read/write support, not just long cursor helpers.
- Updated task and gap-audit documentation to reflect the new operator-facing state:
  - `ai-context/tasks/task-details/01-183.md`;
  - `docs/POSTGRESQL_FULL_PRODUCTION_GAP_AUDIT.md`.

## Verification

- `spring-panel`: `./mvnw.cmd -q -DskipTests compile`

## Notes

- This package turns incidents from “backend domain + API” into a real operator workbench page and turns transport recovery from a compact analytics card into a broader recovery/debug cockpit.
- Repository-wide `spring-panel` tests are still not a clean signal because unrelated historical test-compile failures remain elsewhere, so compile was used as the reliable verification path for this package.
- Remaining `01-183` scope is now mostly endgame hardening:
  - final audit of residual multi-instance side effects outside already leased/claimed flows;
  - docs/runbook closeout;
  - optional deeper long-tail worker forensics or DLQ-specific tooling if needed.

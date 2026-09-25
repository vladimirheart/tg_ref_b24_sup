# 01-274 Phase D analytics saved views/export/event contract R50

Date: 2026-09-25

## Scope

Source-only Phase D continuation after the accepted Kanban phase and the R45 analytics read model.

This slice adds:

- owner-scoped saved analytical views persisted in `task_analytics_views`;
- additive migration chain PostgreSQL V44 / SQLite V55 / MySQL V23;
- CSV export for the currently filtered analytics task slice;
- event-contract v2 for new `TASK_CREATED` events, recording the initial canonical task status;
- coverage-aware time-in-status aggregation that excludes legacy tasks without a complete status timeline;
- Tasks Analytics UI controls to save/load/delete views and export CSV.

## Historical integrity rule

No backfill invents an initial status for historical `TASK_CREATED` rows. Existing legacy events remain unchanged. Time-in-status is reported only for tasks whose enriched `TASK_CREATED` event proves the initial status.

## Runtime boundary

The apply/validation package does not run Flyway, build production images, recreate containers, mutate queues, or touch production data. PostgreSQL V44 must be applied later only by the production `db-migrate` role before deploying a panel image whose readiness verifier requires `task_analytics_views`.

## Status

Phase C remains GREEN. Phase D remains YELLOW pending source checkpoint, V44 production rollout, and manual saved-view/export/time-in-status acceptance.

# 01-274 Phase D analytics read model R45

Date: 2026-09-25
Task: 01-274

## Scope

Source-only first Analytics slice. No runtime, database, queue, environment, image, container, stage, commit or push mutation is performed by the apply/validation runner.

## Functional contract

- Activates the `Аналитика` view inside `/tasks`.
- Adds read-only `/api/task-analytics` under `PAGE_TASKS`.
- Uses canonical `tasks`, normalized project/tag relations and structured `task_events`.
- Filters by project, tag, current status, assignee, source, event type and analysis period.
- Snapshot metrics: total tasks, open tasks and overdue open tasks.
- Period metrics: created tasks, throughput, reopen count and status-change events.
- Event-derived averages: lead time from `created_at` to first completion and cycle time from first `В работе` transition to first completion.
- Returns explicit sample coverage for lead/cycle metrics.
- Does not publish time-in-status yet because historical `TASK_CREATED` events did not capture the initial task status; fabricating those intervals would be misleading.
- Saved analytical views and export remain a later Phase D slice.

## Phase state

Phase C Kanban is accepted functionally in production. 01-274 remains YELLOW while Phase D continues.

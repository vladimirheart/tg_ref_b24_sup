# 01-274 Phase C Kanban UI functional slice R38

Date: 2026-09-25
Task: 01-274

## Scope

Source-only functional Kanban UI slice on top of the R34/R36 board foundation. No production runtime, database, queue, environment, image or container mutation is performed by this runner.

## Behavior

- activates the existing Tasks workspace “Доска” view;
- supports authenticated personal board and explicit project boards;
- loads/ensures boards through the canonical /api/task-boards API;
- renders backend-owned columns and placements without deriving them from task.status;
- supports native drag/drop card placement and ordering;
- supports column add, rename, left/right reorder and safe delete;
- reuses the existing task side-sheet through data-open-task;
- keeps Analytics as pending;
- does not polish the existing Tasks/Projects List UI.

Task 01-274 remains YELLOW pending source checkpoint, migration/deployment and manual Kanban acceptance, followed by Phase D Analytics.

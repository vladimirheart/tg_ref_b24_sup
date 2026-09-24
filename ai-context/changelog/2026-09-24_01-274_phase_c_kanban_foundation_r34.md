# 01-274 Phase C Kanban foundation R34

Date: 2026-09-24
Task: 01-274

## Scope

Source-only Kanban domain/API foundation. No runtime, database, queue, environment, image, container, Git stage, commit, or push mutation is performed by the apply/validation runner.

## Architecture

- `project_boards` stores a canonical scope key for either a project board or one authenticated user's personal board.
- `board_columns` stores editable board-local columns/order.
- `board_task_placements` stores one placement per task per board.
- Board placement is deliberately separate from `tasks.status`.
- Initial placement mirrors the current status where a matching default column exists; drag/drop never rewrites canonical status.
- A multi-project task therefore has independent placement on every project board, while the personal board remains independent as well.
- Moving a card emits structured `BOARD_CARD_MOVED` task events for future analytics.

## Compatibility

- PostgreSQL migration: V43.
- SQLite compatibility migration: V54.
- MySQL compatibility migration: V22.
- Existing Tasks/Projects list UI is not polished or switched to board mode in this slice.

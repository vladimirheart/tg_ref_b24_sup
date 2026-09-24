# 01-274 — Tasks / Projects workspace UI R1

## User request

> теперь пройдёмся по странице задач и самим задачам в проекте:
> задачи должны быть не просто задачами, а с возможностью указать существующий проект, как следствие ещё нужна возможность создавать проекты, в которые эти задачи могут быть добавлены или исключены из любого проекта, плюс необходима доска канбан (для каждого проекта и для отдельно взятого пользователя, по собственным задачам), которую я могу редактировать. и как дополнение - необходима аналитика по задачам, которую могу собрать по разным триггерам задачи, начиная хоть от тега задачи.
> и с UI задачи тоже нужно поработать - найди референсы и предложи в стилистике проекта решение по UI

Follow-up:

> давай пока попробуем так - да

## Baseline

- foundation checkpoint: `db098edaeda396c2d24e0d87d926c2746248d342`;
- Phase A is committed and pushed;
- task `01-274` remains YELLOW.

## Phase B R1 scope

- add `/projects` catalog under existing `PAGE_TASKS` permission;
- project create/edit/archive uses existing `/api/projects` domain API;
- project cards show lead, status and linked task count;
- links from a project open `/tasks?project=<id>`;
- Tasks workspace gets `Все задачи` / `Мои задачи` / project scope controls;
- existing filter form becomes backed by real server-side filtering for number, title, assignee, tag, status, created/due periods and project;
- task list summaries expose normalized project/tag chips;
- replace the large centered task editor with a right-side task sheet;
- task sheet edits multi-project membership and normalized multi-tags;
- structured task events and comments are shown in the task workspace;
- remove non-persisted `customer`, `access_data` and attachment-placeholder fields from the task editor;
- Board and Analytics controls are intentionally visible only as upcoming views in this slice; no Kanban placement or analytics charts are implemented yet.

## Architecture

- filtering/query ownership moves into `TaskQueryService` instead of growing `TaskApiController`;
- `TaskRepository` becomes a `JpaSpecificationExecutor<Task>`;
- project catalog reuses `PAGE_TASKS` rather than adding a new permission domain;
- `projects`, `task_project_memberships`, `tags`, `task_tags` and `task_events` remain the canonical Phase A model;
- no new migration is required for Phase B R1.

## Safety

- source apply/validation only;
- runtime mutation: false;
- DB mutation: false;
- queue mutation: false;
- environment mutation: false;
- no stage/commit/push in this package;
- Maven/Sass validation runs only in an isolated copied workspace;
- only the validated generated `static/css/app.css` is published back after successful isolated validation.

## Status

`01-274` remains YELLOW. Phase C Kanban and Phase D Analytics are still pending, and UI requires manual review before any later closeout.

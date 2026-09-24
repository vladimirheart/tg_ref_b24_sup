# 01-274 — Tasks / Projects domain foundation R1

## User request

> теперь пройдёмся по странице задач и самим задачам в проекте:
> задачи должны быть не просто задачами, а с возможностью указать существующий проект, как следствие ещё нужна возможность создавать проекты, в которые эти задачи могут быть добавлены или исключены из любого проекта, плюс необходима доска канбан (для каждого проекта и для отдельно взятого пользователя, по собственным задачам), которую я могу редактировать. и как дополнение - необходима аналитика по задачам, которую могу собрать по разным триггерам задачи, начиная хоть от тега задачи.
> и с UI задачи тоже нужно поработать - найди референсы и предложи в стилистике проекта решение по UI

Follow-up acceptance of the proposed direction:

> давай пока попробуем так - да

## Read-only audit result

- baseline: `edc97a6a15b5df78ec9bd38abb7f5131704e5be9`;
- audit: GREEN;
- no existing project/tag/event/board domain collides with the proposed model;
- PostgreSQL remains canonical production storage;
- SQLite remains archive/import/test perimeter;
- current task tag is a single compatibility string;
- current task history is free text;
- current Tasks UI is list + modal only.

## Foundation R1

- add projects and task↔project memberships;
- add normalized tags and task↔tag links;
- backfill existing non-empty `tasks.tag` values into normalized tag relations;
- add structured `task_events` for future analytics;
- add Project API;
- keep old `tasks.tag` and existing Tasks UI backward-compatible;
- allow Task API to receive optional multi-tags and project memberships;
- expose project/tag/event detail data through existing task details endpoint;
- emit structured create/update/comment/tag/project events from panel-owned task paths;
- add PostgreSQL readiness probes for the new canonical planning schema;
- add PostgreSQL, SQLite compatibility and MySQL compatibility Flyway sources without executing migrations in this source-apply step.

## Safety

- runtime mutation: false;
- DB mutation: false — migration files are authored but not executed;
- queue mutation: false;
- environment mutation: false;
- stage/commit/push: false in apply/validation package;
- bot JDBC compatibility remains a non-owner of the new project/analytics schema.

## Validation checkpoint

<!-- 01-274_FOUNDATION_R1_VALIDATED_2026-09-24 -->

Foundation R1 source validation completed GREEN on 2026-09-24:

- isolated Maven compile: GREEN;
- targeted tests: GREEN;
- host target untouched: true;
- 26-path source scope preserved;
- 01-274 intentionally remains YELLOW for subsequent UI, Kanban and Analytics phases.

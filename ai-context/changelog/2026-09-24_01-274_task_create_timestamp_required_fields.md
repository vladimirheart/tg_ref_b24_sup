# 01-274 task create PostgreSQL timestamp and required-fields corrective

Date: 2026-09-24
Task: 01-274

## Manual acceptance evidence

Production task creation still returned HTTP 500 after the sequence and CSRF fixes. Read-only runtime diagnostics showed PostgreSQL SQLState 42804 while inserting `tasks`: Hibernate bound `closed_at` as VARCHAR although the production column is `TIMESTAMP WITH TIME ZONE`.

## Root cause

`Task.dueAt`, `Task.closedAt`, and `Task.lastActivityAt` used `LenientOffsetDateTimeConverter`, an `AttributeConverter<OffsetDateTime, String>`. This makes Hibernate bind the converted fields as strings, which is incompatible with PostgreSQL TIMESTAMPTZ writes. `TaskComment.createdAt` used the same mapping and would expose the same defect when comments are added.

## Corrective scope

- Task-domain PostgreSQL timestamp fields use native `OffsetDateTime` persistence instead of the legacy String converter.
- Task comment `created_at` uses native `OffsetDateTime` persistence as well.
- Task create/update validates exactly the required business fields: title, description, assignee.
- Due date/time remains optional.
- Project membership remains optional.
- The task editor provides an explicit `Без проекта` action that clears all project selections.
- A new task still starts without any project selected, even while the list is filtered to a project.
- The task workspace JS cache key is advanced so the corrected client behavior is fetched without a hard refresh.

## Safety

The R23/R24 source package does not mutate runtime, PostgreSQL, queues, environment, Git index, commit history, or remote state. Production rollout remains a separate checkpointed step after isolated validation.

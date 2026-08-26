# Task 01-211 — scale-ready deployment roles kickoff

Date: 2026-08-26

## User prompts

1. «часть проекта запускается с помощью docker. дай экспертную оценку что ещё необходимо запускать с его помощью, например микросервисы, как пример authservice или fileservice»
2. «звучит логично. оформи задачу по правилам проекта с заделом на будущее масштабирование проекта и забирай её в работу»

## Changes

- Created task `01-211`.
- Added it to live AI task list with status `🟡`.
- Defined deployment-role target `panel-web` / `ops-worker` instead of premature microservice decomposition.
- Added migration ownership, background inventory, lease/idempotency, Docker scale and observability requirements.
- Added future extraction guardrails for media, notifications, identity/IdP and integrations.

## Status

Task is intentionally `🟡` (in progress); implementation is not yet marked complete.

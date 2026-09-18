# 2026-09-18 — task 01-236: dynamic bot-runner rotation guard

## Пользовательский запрос

> готово. давай дальше

## Фактический preflight

- Current baseline: `90e246884c910c104d892bad3b75f7fc7fac4a18`.
- PostgreSQL, RabbitMQ, Redis and MinIO are all still classified as `migration_required`; Grafana is already `ready`.
- Live credential discovery/authentication succeeded for all four target components during rehearsal.
- Four-component rehearsal completed successfully without `-Apply`; no credentials, volumes, containers or project files were changed.

## Исправление safety gap

- The credential apply workflow was created before the dedicated dynamic `bot-runner` became the production owner of bot child runtimes.
- PostgreSQL rotation did not recreate `bot-runner`; RabbitMQ/Redis/MinIO only considered legacy static bot services.
- PowerShell and Bash choreography now include a running `bot-runner` for PostgreSQL, RabbitMQ, Redis and MinIO rotation while retaining conditional legacy static services for emergency compatibility.
- The source-contract test and runbook now protect and document this requirement.

## Remaining gate

- This slice does not execute real credential rotation.
- After source validation/apply, live four-component rehearsal must be repeated and explicitly show `bot-runner` in every dependent-service plan.
- Real `-Apply` remains blocked until the off-host rollback/DR prerequisite tracked by `01-212` is resolved or explicitly accepted by the operator.

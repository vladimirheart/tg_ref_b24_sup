# 01-211 Phase C — lifecycle and mixed-bean boundary hardening

Date: 2026-08-26 11:05 +03:00
Task: 01-211

## User prompt

«данные апнул в репо. результат скрипта прикладываю»

Приложенный результат подтвердил успешный Phase B: test-compile, targeted tests и git diff check прошли, после чего изменения были отправлены в репозиторий.

## Findings

Phase C audit found a critical deployment-role issue: class-level `@RuntimeWorkload(WORKER)` removes the whole bean, not only the scheduled method. Several services combine synchronous business methods with scheduler entry points and are dependencies of web/business code.

Also found:
- process-local cursors can be stale after a worker lease handoff;
- production readiness cache was unnecessarily marked singleton and its MeterBinder depended on a worker-only bean;
- RuntimeWorkerCheckpointService still performs runtime DDL even though the table is Flyway-managed;
- attachment metadata reconciliation mutates shared state in `@PostConstruct`;
- monitoring credential key-file fallback is not safe for future multi-host replicas;
- manual long-running RMS/iiko/NetBox operations still execute in process-local executors.

## Changes

- Split mixed business services from worker-only scheduler wrappers.
- Hardened worker checkpoint handoff.
- Moved attachment metadata reconcile to migrator ownership.
- Restricted runtime checkpoint DDL to compatibility `all`.
- Required shared monitoring credentials master key for explicit deployment roles.
- Classified bot process auto-start as compatibility-only.
- Scoped READY/RMS startup hooks.
- Added lifecycle, mixed-service and async-executor source contracts.
- Documented the durable ops-command boundary as the next blocker before Docker compose split.

## Status

01-211 remains `🟡`. Compose split is intentionally deferred until manual long-running operations are dispatched durably to `ops-worker`.

## Verification recovery C1

Первый Phase C запуск подтвердил, что сама split-role crypto protection работает, но тест обнаружил потерю диагностического текста на верхнем уровне исключения:

- expected top-level message to mention `MONITORING_CREDENTIALS_MASTER_KEY`;
- actual top-level message was generic;
- root cause already contained the correct actionable message.

Recovery C1 changes `MonitoringCredentialsCryptoService.loadOrCreateSecretKey()` so an intentional `IllegalStateException` is rethrown unchanged, while other failures keep the generic wrapper with cause.

This is a production operability fix, not a test relaxation.

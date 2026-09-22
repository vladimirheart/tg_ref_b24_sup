# 01-243 traffic readiness regression contract

Date: 2026-09-22

## Existing production behavior

- Traffic readiness is already configured as `db,redis,rabbit`.
- `iguanaProduction` remains a separate operational health/monitoring signal backed by the production-readiness snapshot and Prometheus gauges.
- Read-only production audit R5 completed GREEN for source contract, live traffic readiness and operational Prometheus visibility.
- Operational backlog was `0` during the audit, so no artificial DLQ or failed-delivery record was created.

## Regression protection

- Extend `ApplicationYamlRuntimeContractTest` so the traffic-readiness include property must equal `db,redis,rabbit`.
- Explicitly reject `iguanaProduction` from the traffic-readiness include property.
- Re-run `ApplicationYamlRuntimeContractTest`, `ProductionReadinessHealthIndicatorTest` and `ProductionReadinessServiceTest` to preserve both sides of the separation contract.

## Non-goals

- No application/runtime source change.
- No Docker build, recreate, restart or stop/start.
- No PostgreSQL or RabbitMQ mutation.
- No queue purge/replay/ack.
- No Git stage/commit/push in this source-apply step.

## Final verification

- Read-only production baseline R5 completed GREEN for traffic-readiness source/live probes and the operational Prometheus surface.
- The live operational backlog was `0`; no artificial DLQ or failed-delivery state was created.
- Targeted regression tests completed GREEN and preserve both sides of the contract: Docker traffic readiness is `db,redis,rabbit`, while degraded production-readiness semantics remain observable separately.
- Commit `3fc72dfdc8848e48508723ab9e094d56b6ea291e` was pushed from parent `66914f371a7aa066538b5255bd54d74f55af222f` after exact staging and a fresh remote guard.
- Independent GitHub verification confirmed `ahead_by=1`, `behind_by=0` and exactly the three expected files.
- No application/runtime source, Docker runtime, PostgreSQL data, RabbitMQ queue, or environment state was mutated by this regression/closeout slice.
- Task `01-243` is now `🟣`: AI work complete, awaiting manual user acceptance. Only the user may mark it `🟢`.

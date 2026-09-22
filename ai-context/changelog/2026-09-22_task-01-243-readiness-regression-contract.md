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

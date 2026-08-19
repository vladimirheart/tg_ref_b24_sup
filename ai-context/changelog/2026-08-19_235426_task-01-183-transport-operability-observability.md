# Changelog

## Summary

- added canonical `integration_transport_operation_log` migrations for durable transport recovery audit trail
- extended integration transport ops API with alerts, worker checkpoint diagnostics, event operation history, and ticket-scoped transport debug
- added separate stale-checkpoint signal incident monitoring alongside existing transport degradation monitoring
- expanded incident workbench transport tab with observability alerts, recent recovery operations, richer checkpoint health, and targeted ticket debug
- covered the new transport operability surface with service and monitor tests

## Files

- `spring-panel/src/main/java/com/example/panel/controller/AnalyticsIntegrationTransportController.java`
- `spring-panel/src/main/java/com/example/panel/service/integration/IntegrationTransportIncidentMonitor.java`
- `spring-panel/src/main/java/com/example/panel/service/integration/IntegrationTransportOpsService.java`
- `spring-panel/src/main/resources/db/migration/postgresql/V26__integration_transport_operation_log.sql`
- `spring-panel/src/main/resources/db/migration/sqlite/V47__integration_transport_operation_log.sql`
- `spring-panel/src/main/resources/static/js/incidents-workbench.js`
- `spring-panel/src/main/resources/templates/incidents/index.html`
- `spring-panel/src/test/java/com/example/panel/service/integration/IntegrationTransportIncidentMonitorTest.java`
- `spring-panel/src/test/java/com/example/panel/service/integration/IntegrationTransportOpsServiceTest.java`
- `docs/runbooks/postgresql-production-contour.md`
- `docs/POSTGRESQL_FULL_PRODUCTION_GAP_AUDIT.md`
- `ai-context/tasks/task-details/01-183.md`

## Prompt

- `продолжи`
- `deeper replay/debug/compensation surface вокруг integration workers;`
- `более широкий observability/alerting хвост;`

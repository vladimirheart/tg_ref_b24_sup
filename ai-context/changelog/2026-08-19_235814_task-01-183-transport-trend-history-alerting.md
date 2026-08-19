# Changelog

## Summary

- added canonical `integration_transport_health_snapshots` migrations plus a leased snapshot scheduler for historical transport pressure tracking
- extended transport ops backend with health snapshots, trend summaries, sustained-pressure alerts, and manual-compensation pressure semantics
- added a separate sustained-pressure signal incident on top of the existing transport and checkpoint degradation incidents
- expanded analytics `Integration Transport Ops` dashboard with trend badge, recent snapshot history, richer checkpoint health, alerts, and recent recovery operations
- covered the new trend/history incident surface with targeted service and monitor tests

## Files

- `spring-panel/src/main/java/com/example/panel/service/integration/IntegrationTransportHealthSnapshotScheduler.java`
- `spring-panel/src/main/java/com/example/panel/service/integration/IntegrationTransportIncidentMonitor.java`
- `spring-panel/src/main/java/com/example/panel/service/integration/IntegrationTransportOpsService.java`
- `spring-panel/src/main/resources/db/migration/postgresql/V27__integration_transport_health_snapshots.sql`
- `spring-panel/src/main/resources/db/migration/sqlite/V48__integration_transport_health_snapshots.sql`
- `spring-panel/src/main/resources/static/js/analytics-integration-transport.js`
- `spring-panel/src/main/resources/templates/analytics/index.html`
- `spring-panel/src/test/java/com/example/panel/service/integration/IntegrationTransportIncidentMonitorTest.java`
- `spring-panel/src/test/java/com/example/panel/service/integration/IntegrationTransportOpsServiceTest.java`
- `docs/runbooks/postgresql-production-contour.md`
- `docs/POSTGRESQL_FULL_PRODUCTION_GAP_AUDIT.md`
- `ai-context/tasks/task-details/01-183.md`

## Prompt

- `давай дальше`

# Changelog

## Summary

- added canonical worker health snapshot storage for integration transport watchers with leased capture and retention cleanup
- expanded transport ops with per-worker diagnostics, trend summaries, history, recommendations, and worker-specific related incidents
- opened worker-specific checkpoint degradation incidents and added worker drilldown from analytics and incident workbench
- covered the new worker forensics surface with controller/service/monitor tests and synced the production runbook/task context

## Files

- `spring-panel/src/main/java/com/example/panel/controller/AnalyticsIntegrationTransportController.java`
- `spring-panel/src/main/java/com/example/panel/service/IncidentService.java`
- `spring-panel/src/main/java/com/example/panel/service/integration/IntegrationTransportHealthSnapshotScheduler.java`
- `spring-panel/src/main/java/com/example/panel/service/integration/IntegrationTransportIncidentMonitor.java`
- `spring-panel/src/main/java/com/example/panel/service/integration/IntegrationTransportOpsService.java`
- `spring-panel/src/main/resources/db/migration/postgresql/V28__integration_transport_worker_health_snapshots.sql`
- `spring-panel/src/main/resources/db/migration/sqlite/V49__integration_transport_worker_health_snapshots.sql`
- `spring-panel/src/main/resources/static/js/analytics-integration-transport.js`
- `spring-panel/src/main/resources/static/js/incidents-workbench.js`
- `spring-panel/src/main/resources/templates/analytics/index.html`
- `spring-panel/src/test/java/com/example/panel/controller/AnalyticsIntegrationTransportControllerWebMvcTest.java`
- `spring-panel/src/test/java/com/example/panel/service/integration/IntegrationTransportIncidentMonitorTest.java`
- `spring-panel/src/test/java/com/example/panel/service/integration/IntegrationTransportOpsServiceTest.java`
- `docs/runbooks/postgresql-production-contour.md`
- `ai-context/tasks/task-details/01-183.md`

## Prompt

- `продолжи по задаче 01-183:
Остались strict active-active webhook/session-sharing edge cases для одного канала, deeper worker-specific replay/forensics beyond текущего panel-side history/debug, и более широкий внешний observability/alerting closeout. Следующий логичный пакет можно брать именно в эту сторону.`

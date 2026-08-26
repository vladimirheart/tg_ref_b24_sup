# Runtime deployment roles and background workload inventory

Date: 2026-08-26
Task: 01-211
Status: Phase A foundation; production compose is not split yet.

## Runtime role contract

- `APP_RUNTIME_ROLE=all` — compatibility/local default; all classified workloads remain enabled.
- `APP_RUNTIME_ROLE=web` or `panel-web` — only web-compatible classified workloads are registered.
- `APP_RUNTIME_ROLE=worker` or `ops-worker` — only worker-compatible classified workloads are registered.
- `APP_INSTANCE_ID` identifies the process in logs, actuator info and metrics tags.

Role selection is explicit configuration. It must not depend on hostname or process-name heuristics.

## Replica policy vocabulary

- `PROCESS_LOCAL` — per-process behavior is expected; safe to exist independently on each allowed replica.
- `LEASED` — shared side effect is protected by `RuntimeCoordinationService.runWithLease(...)`.
- `BROKER_COMPETING_CONSUMER` — RabbitMQ broker/container semantics distribute deliveries between worker consumers.
- `SINGLETON` — multi-worker safety has not been proven. Keep worker replica count at 1 until the workload is lease/claim/idempotency hardened.

## Current classified background entry points

| Workload | Source | Trigger | Intended role | Replica policy | Evidence |
| --- | --- | --- | --- | --- | --- |
| `ai-offline-evaluation-service` | `spring-panel/src/main/java/com/example/panel/service/AiOfflineEvaluationService.java` | scheduled | `worker` | `LEASED` | RuntimeCoordinationService.runWithLease detected |
| `backup-readiness-monitoring-scheduler` | `spring-panel/src/main/java/com/example/panel/service/BackupReadinessMonitoringScheduler.java` | scheduled | `worker` | `LEASED` | RuntimeCoordinationService.runWithLease detected |
| `bot-runtime-blacklist-service` | `spring-panel/src/main/java/com/example/panel/service/BotRuntimeBlacklistService.java` | scheduled | `worker` | `LEASED` | RuntimeCoordinationService.runWithLease detected |
| `conversation-ticket-created-listener` | `spring-panel/src/main/java/com/example/panel/service/integration/ConversationTicketCreatedListener.java` | rabbit | `worker` | `BROKER_COMPETING_CONSUMER` | RabbitMQ competing consumer delivery semantics |
| `credential-rotation-registry-scheduler` | `spring-panel/src/main/java/com/example/panel/service/CredentialRotationRegistryScheduler.java` | scheduled | `worker` | `LEASED` | RuntimeCoordinationService.runWithLease detected |
| `dialog-auto-close-scheduler-service` | `spring-panel/src/main/java/com/example/panel/service/DialogAutoCloseSchedulerService.java` | scheduled | `worker` | `LEASED` | RuntimeCoordinationService.runWithLease detected |
| `feedback-prompt-dispatch-scheduler-service` | `spring-panel/src/main/java/com/example/panel/service/FeedbackPromptDispatchSchedulerService.java` | scheduled | `worker` | `LEASED` | RuntimeCoordinationService.runWithLease detected |
| `hikari-pool-pressure-reporter` | `spring-panel/src/main/java/com/example/panel/config/HikariPoolPressureReporter.java` | scheduled | `web+worker` | `PROCESS_LOCAL` | process-local datasource pressure metric/reporting |
| `housekeeping-scheduler` | `spring-panel/src/main/java/com/example/panel/background/HousekeepingScheduler.java` | scheduled | `worker` | `LEASED` | RuntimeCoordinationService.runWithLease detected |
| `iiko-api-monitoring-scheduler` | `spring-panel/src/main/java/com/example/panel/service/IikoApiMonitoringScheduler.java` | scheduled | `worker` | `LEASED` | RuntimeCoordinationService.runWithLease detected |
| `iiko-department-locations-sync-scheduler` | `spring-panel/src/main/java/com/example/panel/service/IikoDepartmentLocationsSyncScheduler.java` | scheduled | `worker` | `LEASED` | RuntimeCoordinationService.runWithLease detected |
| `inbound-client-message-listener` | `spring-panel/src/main/java/com/example/panel/service/integration/InboundClientMessageListener.java` | rabbit | `worker` | `BROKER_COMPETING_CONSUMER` | RabbitMQ competing consumer delivery semantics |
| `incident-ops-escalation-service` | `spring-panel/src/main/java/com/example/panel/service/IncidentOpsEscalationService.java` | scheduled | `worker` | `LEASED` | RuntimeCoordinationService.runWithLease detected |
| `incident-route-delivery-outbox-service` | `spring-panel/src/main/java/com/example/panel/service/IncidentRouteDeliveryOutboxService.java` | scheduled | `worker` | `LEASED` | RuntimeCoordinationService.runWithLease detected |
| `integration-transport-health-snapshot-scheduler` | `spring-panel/src/main/java/com/example/panel/service/integration/IntegrationTransportHealthSnapshotScheduler.java` | scheduled | `worker` | `LEASED` | RuntimeCoordinationService.runWithLease detected |
| `integration-transport-incident-monitor` | `spring-panel/src/main/java/com/example/panel/service/integration/IntegrationTransportIncidentMonitor.java` | scheduled | `worker` | `LEASED` | RuntimeCoordinationService.runWithLease detected |
| `knowledge-base-notion-sync-scheduler` | `spring-panel/src/main/java/com/example/panel/background/KnowledgeBaseNotionSyncScheduler.java` | scheduled | `worker` | `LEASED` | RuntimeCoordinationService.runWithLease detected |
| `monitoring-check-history-retention-service` | `spring-panel/src/main/java/com/example/panel/service/MonitoringCheckHistoryRetentionService.java` | scheduled | `worker` | `LEASED` | RuntimeCoordinationService.runWithLease detected |
| `net-box-object-passport-sync-scheduler` | `spring-panel/src/main/java/com/example/panel/service/NetBoxObjectPassportSyncScheduler.java` | scheduled | `worker` | `LEASED` | RuntimeCoordinationService.runWithLease detected |
| `operator-notification-watcher` | `spring-panel/src/main/java/com/example/panel/service/OperatorNotificationWatcher.java` | scheduled | `worker` | `LEASED` | RuntimeCoordinationService.runWithLease detected |
| `outbound-feedback-prompt-publish-outbox-service` | `spring-panel/src/main/java/com/example/panel/service/integration/OutboundFeedbackPromptPublishOutboxService.java` | scheduled | `worker` | `LEASED` | RuntimeCoordinationService.runWithLease detected |
| `production-readiness-observation-cache` | `spring-panel/src/main/java/com/example/panel/observability/ProductionReadinessObservationCache.java` | scheduled | `worker` | `SINGLETON` | no shared lease/claim proof detected; keep worker singleton |
| `provider-delivery-alerting-scheduler` | `spring-panel/src/main/java/com/example/panel/service/ProviderDeliveryAlertingScheduler.java` | scheduled | `worker` | `LEASED` | RuntimeCoordinationService.runWithLease detected |
| `provider-health-monitoring-scheduler` | `spring-panel/src/main/java/com/example/panel/service/ProviderHealthMonitoringScheduler.java` | scheduled | `worker` | `LEASED` | RuntimeCoordinationService.runWithLease detected |
| `public-ingress-monitoring-scheduler` | `spring-panel/src/main/java/com/example/panel/service/PublicIngressMonitoringScheduler.java` | scheduled | `worker` | `LEASED` | RuntimeCoordinationService.runWithLease detected |
| `rms-license-monitoring-scheduler` | `spring-panel/src/main/java/com/example/panel/service/RmsLicenseMonitoringScheduler.java` | scheduled | `worker` | `LEASED` | RuntimeCoordinationService.runWithLease detected |
| `sidebar-bot-status-watcher` | `spring-panel/src/main/java/com/example/panel/service/SidebarBotStatusWatcher.java` | scheduled | `web` | `PROCESS_LOCAL` | process-local UI/SSE workload |
| `sidebar-status-watcher` | `spring-panel/src/main/java/com/example/panel/service/SidebarStatusWatcher.java` | scheduled | `web` | `PROCESS_LOCAL` | process-local UI/SSE workload |
| `sla-escalation-webhook-notifier` | `spring-panel/src/main/java/com/example/panel/service/SlaEscalationWebhookNotifier.java` | scheduled | `worker` | `LEASED` | RuntimeCoordinationService.runWithLease detected |
| `smtp-notification-monitoring-scheduler` | `spring-panel/src/main/java/com/example/panel/service/SmtpNotificationMonitoringScheduler.java` | scheduled | `worker` | `LEASED` | RuntimeCoordinationService.runWithLease detected |
| `ssl-certificate-monitoring-scheduler` | `spring-panel/src/main/java/com/example/panel/service/SslCertificateMonitoringScheduler.java` | scheduled | `worker` | `LEASED` | RuntimeCoordinationService.runWithLease detected |
| `ui-event-outbox-watcher` | `spring-panel/src/main/java/com/example/panel/service/UiEventOutboxWatcher.java` | scheduled | `worker` | `LEASED` | RuntimeCoordinationService.runWithLease detected |
| `ui-event-stream-heartbeat-scheduler` | `spring-panel/src/main/java/com/example/panel/service/UiEventStreamHeartbeatScheduler.java` | scheduled | `web` | `PROCESS_LOCAL` | process-local UI/SSE workload |
| `workspace-guardrail-webhook-notifier` | `spring-panel/src/main/java/com/example/panel/service/WorkspaceGuardrailWebhookNotifier.java` | scheduled | `worker` | `LEASED` | RuntimeCoordinationService.runWithLease detected |
## Known blockers before switching production compose to `panel-web + ops-worker`

1. UI realtime fanout is still instance-local (`UiEventStreamService` owns in-memory SSE emitters). Worker-owned ingestion/notification jobs can persist business effects, but a worker cannot directly reach SSE emitters connected to another web process. Before the compose split, add a distributed UI event bridge (Redis pub/sub or equivalent backend event fanout) so every web replica can emit the event to its own clients.
2. Flyway/startup migration ownership is still shared by the application startup path. Phase D must introduce a one-shot `db-migrate` role or another explicit single-owner migration guard before multi-instance production startup.
3. Any workload classified `SINGLETON` blocks `ops-worker x N`. It must either remain single-replica or be hardened with a lease/claim/idempotency contract.
4. `PanelApplication` startup runners and other non-`@Scheduled` lifecycle hooks still require a separate role audit. This Phase A slice guards scheduled methods and panel RabbitMQ listeners first; startup/migration hooks are tracked as the next slice.
5. `docker-compose.production-contour.yml` and nginx are intentionally unchanged in Phase A. The deployment topology must change only after role activation and cross-role event fanout are proven.

## Scale invariant

The role split is a deployment boundary, not a business ownership split. `panel-web` and `ops-worker` remain the same canonical backend and may use backend-owned PostgreSQL repositories. Channel/transport workers (`bot-*` and future external adapters) still integrate through queue/API boundaries and must not become owners of the business schema.

## Phase C boundary correction

Class-level workload conditions must be attached only to beans whose entire lifecycle belongs to one deployment role.

A service that exposes synchronous business methods and also contains a scheduled trigger is a mixed bean. Its business service must remain shared; the scheduled trigger belongs in a separate worker-only wrapper.

Phase C applies this split to AI offline evaluation, blacklist expiry, incident escalation, incident route delivery and outbound feedback publishing.

`ProductionReadinessObservationCache` is now `PROCESS_LOCAL`, not `SINGLETON`, because its refresh is read-only and only updates local metric state.

### Remaining blocker before compose split

Manual RMS/iiko/NetBox actions still execute through process-local executors. Production compose must not be split until operator requests enqueue durable backend commands and `ops-worker` owns execution. See `docs/runtime-async-command-boundary.md`.

## Phase E production topology

Phase A-C inventory blockers are now closed by later slices:

- distributed UI fanout: Redis;
- migration owner: one-shot `db-migrate`;
- mixed service/scheduler beans: separated where required;
- process-local manual async work: replaced for explicit roles by `backend_ops_command`;
- production readiness cache: `PROCESS_LOCAL`, not `SINGLETON`.

Production Compose now uses:

```text
db-migrate -> ops-worker x M -> panel-web x N
```

`panel-web` and `ops-worker` have no host port publishing. `panel-direct` keeps loopback `127.0.0.1:8080`; optional public nginx routes only to `panel-web`.

The remaining task gate is the real Docker role/scale smoke. Multi-host orchestration remains out of scope.

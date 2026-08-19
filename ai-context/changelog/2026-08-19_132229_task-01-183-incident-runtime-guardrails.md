# 2026-08-19 13:22:29 - task-01-183 incident runtime guardrails

## User prompt

`забирай в работу:
broader infra/incidents scope;
final cleanup remaining non-canonical contours, где ещё остаётся production debt;`

## What changed

- В `spring-panel` добавлен canonical incident module на backend-owned storage:
  - новые сущности `Incident`, `IncidentRelation`, `IncidentEvent`, `IncidentWatcher`, `IncidentRoute`;
  - новые repositories и `IncidentService`;
  - новый `IncidentApiController` с CRUD/event/watcher/route API;
  - Flyway migrations для `postgresql`, `sqlite`, `mysql`.
- Incident domain встроен в живые operator-facing read paths:
  - workspace context теперь возвращает `incidents`;
  - `TaskApiController` возвращает incident summaries для задачи;
  - `ObjectPassportApiController` получил `/api/object_passports/{id}/incidents`.
- `NotificationRoutingService` получил default routing scope `incidents`.
- `DialogWorkspacePayloadAssemblerService` переведён с хрупкого `Map.of(...)` на `mapWithNullableValues(...)` для расширенного context payload.
- `PostgresRuntimeReadinessVerifier` теперь дополнительно проверяет:
  - transport tables `ui_event_outbox` и `integration_inbound_event_inbox`;
  - incident tables `incidents`, `incident_relations`, `incident_events`, `incident_watchers`, `incident_routes`;
  - readiness log теперь включает `incidents` и `open_incidents`.
- Добавлены/обновлены targeted tests:
  - `IncidentServiceTest`
  - `IncidentApiControllerWebMvcTest`
  - `PostgresRuntimeReadinessVerifierTest`
  - `ObjectPassportApiControllerWebMvcTest`
  - `DialogWorkspacePayloadAssemblerServiceTest`
- Обновлены task/docs:
  - `ai-context/tasks/task-details/01-183.md`
  - `docs/POSTGRESQL_FULL_PRODUCTION_GAP_AUDIT.md`
  - `docs/target-production-architecture-plan.md`

## Verification

- `spring-panel`: `.\mvnw.cmd "-Dtest=PostgresRuntimeReadinessVerifierTest,DialogWorkspacePayloadAssemblerServiceTest,ObjectPassportApiControllerWebMvcTest,IncidentServiceTest,IncidentApiControllerWebMvcTest" test`

## Notes

- Remaining production debt после этого пакета смещён уже не на live SQLite datasource graph, а на infra contour: `Redis`/leases/live coordination, `MinIO/S3` attachment boundary, stateless multi-instance coordination и richer operator/ops слой вокруг нового incident module.

# 01-211 Phase A — runtime roles and workload classification

Date: 2026-08-26 09:05 +03:00
Task: 01-211

## User prompt

«хорошо. репо обновил. приступай к выполнению задачи 01-211»

## Changes

- Added explicit Iguana runtime-role contract (`all`, `web/panel-web`, `worker/ops-worker`).
- Added conditional `@RuntimeWorkload` metadata with explicit replica policy.
- Classified every current spring-panel `@Scheduled` and panel `@RabbitListener` source at patch time.
- Worker jobs using `runWithLease` are marked `LEASED`; unproven worker jobs are conservatively marked `SINGLETON`.
- RabbitMQ listeners are marked worker-owned competing consumers.
- Kept sidebar/SSE heartbeat workloads web-local and Hikari pressure reporting process-local on both backend roles.
- Split SSE heartbeat scheduling from the shared `UiEventStreamService`.
- Added startup/actuator diagnostics and metrics tags for role/instance.
- Added source contract tests so future background entry points cannot be added without classification.
- Added `docs/runtime-deployment-roles.md` inventory and documented blockers before the actual compose split.

## Important finding

`UiEventStreamService` is process-local because SSE emitters live in memory. Several worker-side flows call realtime publication after durable business work. The production compose must not be split until a distributed UI event fanout is added, otherwise worker events cannot reach SSE clients connected to separate web replicas.

## Status

01-211 remains `🟡`. This changelog records Phase A implementation, not task completion.

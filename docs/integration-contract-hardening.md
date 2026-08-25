# Integration Contract Hardening

## Purpose

This document describes the production contract for Iguana ingress and egress integrations:

- public platform webhooks routed through `spring-panel`;
- internal bot API traffic between `java-bot` and `spring-panel`;
- queue-backed inbound/outbound transport on RabbitMQ;
- operator recovery paths for replay, requeue and failed delivery diagnostics.

It complements:

- [BOT_RUNTIME_CONTRACT.md](./BOT_RUNTIME_CONTRACT.md)
- [observability-baseline.md](./observability-baseline.md)
- [runbooks/production-launch-checklist.md](./runbooks/production-launch-checklist.md)

## 1. Public Ingress Rules

### 1.1 Platform webhooks

Public platform webhooks must terminate on `spring-panel`, not on standalone bot processes.

Current routing rules:

- `VK` and `MAX` public webhook URLs are panel-owned ingress points.
- Bot runtime processes listen only on local/internal addresses.
- Channel-specific webhook endpoints must include the channel identifier in the public route when the provider contract is channel-bound.

Reference rule: `ai-context/rules/backend/01-bot-webhook-routing.md`.

### 1.2 MAX webhook relay

For `MAX`, `spring-panel` accepts the public request and relays it to the local bot runtime.

Production requirements:

- keep the bot listener internal-only;
- configure `MAX_WEBHOOK_SECRET` / channel secret and validate it end-to-end;
- treat `502/503` from relay path as provider-facing delivery failures and alert on sustained error rate;
- avoid public direct exposure of bot ports.

## 2. Internal Bot API Contract

### 2.1 Scope

Internal bot API is the control/data plane used by `java-bot` for:

- ticket reads;
- runtime config reads;
- blacklist/unblock reads;
- write mutations such as reopen, activity updates, operator relay, feedback submit and channel support-chat sync.

Base path:

- `/internal/api/bot/**`

### 2.2 Authentication

Every request must carry:

- `X-Iguana-Bot-Api-Token`

Panel-side source of truth:

- `APP_INTERNAL_BOT_API_TOKEN`

Bot-side runtime variables:

- `APP_PANEL_INTERNAL_API_BASE_URL`
- `APP_PANEL_INTERNAL_API_TOKEN`

Production rule:

- the token must be rotated out of the built-in default before go-live;
- token reuse across unrelated environments is not allowed;
- only bot runtimes and trusted local/system components may call this API.

### 2.3 Request signing

The hardened contract supports signed requests with:

- `X-Iguana-Request-Timestamp`
- `X-Iguana-Request-Signature`

Signature model:

- algorithm: `HMAC-SHA256`
- canonical payload:
  `METHOD + "\n" + PATH_WITH_QUERY + "\n" + TIMESTAMP + "\n" + SHA256(BODY_BYTES)`
- secret: `APP_INTERNAL_BOT_API_SIGNATURE_SECRET` when provided, otherwise fallback to the internal API token

Panel-side controls:

- `APP_INTERNAL_BOT_API_REQUIRE_REQUEST_SIGNATURE`
- `APP_INTERNAL_BOT_API_REQUEST_TIMESTAMP_SKEW`

Bot-side controls:

- `APP_PANEL_INTERNAL_API_REQUEST_SIGNING_ENABLED`
- `APP_PANEL_INTERNAL_API_SIGNATURE_SECRET`

Production recommendation:

- enable signing on all bot runtimes immediately;
- after rollout, set `APP_INTERNAL_BOT_API_REQUIRE_REQUEST_SIGNATURE=true` on the panel;
- keep allowed timestamp skew small, default `5m`.

### 2.4 Idempotency for write requests

Write requests may include:

- `X-Iguana-Idempotency-Key`

Panel behavior:

- first request claims the key;
- repeated request with the same method/path/key replays the cached successful response;
- concurrent in-flight duplicate returns conflict until the first execution resolves.

Storage behavior:

- Redis-backed when `APP_COORDINATION_MODE=redis`;
- local in-memory fallback in non-Redis/dev contours.

Relevant panel controls:

- `APP_INTERNAL_BOT_API_IDEMPOTENCY_INFLIGHT_TTL`
- `APP_INTERNAL_BOT_API_IDEMPOTENCY_TTL`

### 2.5 Retry policy

`java-bot` panel clients now support bounded retries for internal write requests.

Bot-side controls:

- `APP_PANEL_INTERNAL_API_REQUEST_TIMEOUT`
- `APP_PANEL_INTERNAL_API_RETRY_ATTEMPTS`
- `APP_PANEL_INTERNAL_API_RETRY_BACKOFF`

Recommended production defaults:

- timeout: `5s`
- retry attempts: `2`
- linear backoff: `250ms`

Rule of thumb:

- retry only bounded, fast control-plane calls;
- rely on idempotency key reuse across retries;
- do not convert persistent authorization/configuration problems into infinite retries.

## 3. Queue-backed Transport

### 3.1 Inbound transport

Inbound client messages are accepted into RabbitMQ and consumed by `spring-panel`.

Key protections already in place:

- inbox table `integration_inbound_event_inbox`;
- event-level dedup by `event_id`;
- stale processing reclaim;
- failed event visibility in analytics transport diagnostics;
- manual replay actions from transport analytics UI/API.

Failure modes covered:

- duplicate delivery from provider or broker;
- worker crash during processing;
- poison message routed into DLQ;
- manual operator replay after remediation.

### 3.2 Outbound transport

Bot-side outbound integration events use queue/outbox semantics.

Important layers:

- RabbitMQ outbound exchanges and per-channel queues;
- outbox tables and dispatch workers;
- failed message requeue from transport analytics;
- incident route delivery outbox for support/ops notifications.

### 3.3 Dead-letter and replay

Current DLQ/recovery contour includes:

- inbound DLQ queues for integration ingress;
- ticket-created DLQ queues;
- outbox event requeue actions;
- incident route replay and failed-route redelivery;
- operation log for manual replay/requeue actions.

Production requirement:

- DLQ must be monitored as a first-class signal;
- replay/requeue must be operator-driven and auditable;
- recurring replay volume should create or update an incident rather than stay silent.

## 4. Operator Diagnostics

Existing operator/debug tools:

- `/api/analytics/integration-transport`
- inbound event detail and replay endpoints
- outbound event detail and requeue endpoints
- ticket-scoped transport debug
- worker checkpoint diagnostics
- transport incident linkage

What operators should always be able to answer:

- which event failed;
- whether it was auth, payload, timeout, provider or broker related;
- whether the event is safe to replay;
- whether replay already happened and by whom;
- whether failure is isolated or systemic.

## 5. Production Checklist For Integrations

Before go-live, verify all of the following:

1. `APP_INTERNAL_BOT_API_TOKEN` is rotated and not default.
2. `APP_SECURITY_REMEMBER_ME_KEY` is rotated and not default.
3. `APP_INTERNAL_BOT_API_REQUIRE_REQUEST_SIGNATURE=true` is enabled after bot rollout.
4. Bot runtimes receive `APP_PANEL_INTERNAL_API_REQUEST_SIGNING_ENABLED=true`.
5. If using a dedicated signing secret, both sides use the same `APP_INTERNAL_BOT_API_SIGNATURE_SECRET`.
6. RabbitMQ DLQ queues are created and scrape/alerted.
7. Redis coordination is enabled for multi-instance or production contours.
8. Transport analytics and readiness dashboards are reachable by support/ops staff.
9. Replay/requeue access is limited to trusted operator/admin roles.
10. Failed delivery runbook and rollback path are documented for the on-call team.

## 6. Recommended Alerts

Minimum alert set:

- internal bot API 401/403 spike;
- internal bot API 5xx spike;
- duplicate/in-flight idempotency conflicts sustained above normal baseline;
- inbound DLQ message count above zero for 10 minutes;
- outbound failed backlog above threshold;
- stale inbound or outbound processing records;
- incident route delivery failed backlog above zero;
- worker checkpoint stale or lagging.

## 7. What This Task Hardened

This baseline introduces:

- signed internal bot API requests with timestamp verification;
- replay-safe idempotency for bot write mutations;
- bounded client retries in `java-bot` panel write clients;
- explicit environment contract for request signing and retry knobs;
- documentation for ingress/auth/retry/replay/operator diagnostics.

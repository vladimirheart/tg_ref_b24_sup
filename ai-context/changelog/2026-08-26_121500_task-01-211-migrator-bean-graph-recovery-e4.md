# 01-211 Phase E4 — migrator bean-graph recovery

Date: 2026-08-26 12:15 +03:00
Task: 01-211

## Runtime evidence

The real Docker smoke reached `db-migrate`.

Flyway successfully validated and applied all PostgreSQL migrations through V40:

```text
Successfully applied 40 migrations to schema "public", now at version v40
```

The process failed afterwards while creating the Spring bean graph:

```text
DialogWorkspaceSlaViewService
  -> SlaEscalationWebhookNotifier

No qualifying bean of type
com.example.panel.service.SlaEscalationWebhookNotifier
```

## Root cause

`SlaEscalationWebhookNotifier` mixed two responsibilities:

1. shared business methods used by `DialogWorkspaceSlaViewService`, including routing policy preview;
2. a scheduled worker entry point.

The whole class was classified as:

```text
RuntimeRole.WORKER
RuntimeReplicaPolicy.LEASED
```

so the bean disappeared from both MIGRATOR and WEB contexts.

## Recovery

- keep `SlaEscalationWebhookNotifier` as a shared service;
- remove `@Scheduled` and runtime-role classification from the shared service;
- add `SlaEscalationWebhookScheduler`;
- move the scheduled entry point and WORKER/LEASED classification to that wrapper;
- preserve the existing lease implementation inside the notifier;
- extend the mixed-service boundary source-contract;
- rebuild the panel image before repeating the full Docker smoke.

## Status

Task remains `🟡` until the full Docker role/scale smoke is green.

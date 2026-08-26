# 01-211 Phase E6 — smoke workload-id contract recovery

Date: 2026-08-26 12:45 +03:00
Task: 01-211

## Runtime evidence

The post-E5 Docker smoke advanced past all prior blockers:

```text
db-migrate completed with exit code 0
ops-worker-1 Healthy
ops-worker-2 Healthy
panel-web-1 Healthy
panel-web-2 Healthy
nginx Started
panel-direct Started
```

It then failed only on the verifier assertion:

```text
panel-web is missing web SSE heartbeat workload.
```

## Root cause

The runtime source declares:

```java
@RuntimeWorkload(
    id = "ui-event-stream-heartbeat",
    roles = {RuntimeRole.WEB},
    replicaPolicy = RuntimeReplicaPolicy.PROCESS_LOCAL
)
```

The Docker smoke script was checking a non-existent id:

```text
ui-event-stream-heartbeat-scheduler
```

in both the positive WEB assertion and the negative WORKER assertion.

## Recovery

- replace both stale smoke assertions with the declared workload id;
- add a Docker topology source-contract that reads both the runtime source and
  the smoke script and requires the same id;
- rerun PowerShell parser, Spring source-contract tests, Compose config and
  `git diff --check`.

No production runtime Java behavior changes in E6.

## Status

Task remains `🟡` until the full Docker role/scale smoke is green.

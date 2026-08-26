# 01-211 Phase E7 — ingress-readiness smoke recovery

Date: 2026-08-26 13:00 +03:00
Task: 01-211

## Runtime evidence

The post-E6 smoke reached all of these gates successfully:

```text
db-migrate completed with exit code 0.
Runtime role/workload isolation verified.
Web/worker migration ownership skip verified.
```

Compose also reported both ingress containers as `Started`.

The next operation failed on the first host-side edge request:

```text
Invoke-WebRequest : underlying connection was unexpectedly closed
scripts/docker-production-role-smoke.ps1:296
```

## Root cause in verifier

Both `nginx` and `panel-direct` have Docker healthchecks, but the smoke only waited
for `panel-web` and `ops-worker`. It immediately called the published edge port
after Compose returned `Started`, which creates a startup race between the
container process/entrypoint and the host-side HTTP assertion.

## Recovery

- wait for exactly one `nginx` and one `panel-direct` container;
- require both ingress containers to become healthy;
- retry initial `/actuator/info` until it reports runtime role `web`;
- retain existing routing/security and replica failover assertions afterwards;
- add a source-contract for ingress readiness sequencing.

No production runtime or Compose behavior changes in E7.

## Status

Task remains `🟡` until the full Docker role/scale smoke is green.

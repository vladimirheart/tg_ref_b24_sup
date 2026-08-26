# 01-211 Phase E9 — Windows PowerShell HTTP byte-array smoke recovery

Date: 2026-08-26 14:00 +03:00
Task: 01-211

## Runtime evidence

The kept post-E8 stack proves the scale-ready topology is already routing correctly.

Public edge:

```text
GET /actuator/info -> HTTP 200
Content-Type: application/vnd.spring-boot.actuator.v3+json;charset=UTF-8
```

nginx direct service call:

```json
{"iguanaRuntime":{"role":"web", ...}}
```

Both panel-web replicas independently return the same runtime contract with
different instance ids.

The Windows PowerShell host diagnostic renders `Invoke-WebRequest.Content` as:

```text
123 34 105 103 117 97 110 97 82 ...
```

Those are UTF-8 bytes for `{"iguanaRuntime"...}`.

## Root cause

The smoke verifier used:

```powershell
$response.Content | ConvertFrom-Json
```

Windows PowerShell 5.1 enumerates the byte array through the pipeline, so
`ConvertFrom-Json` receives numeric byte values instead of the UTF-8 JSON text.
The resulting object cannot expose `iguanaRuntime`, causing a false timeout even
though nginx and panel-web are healthy and returning the correct body.

## Recovery

- add `Convert-HttpResponseContentToJson`;
- UTF-8 decode byte-array content before JSON parsing;
- use the helper for ingress readiness, web replica failover and worker restart
  availability checks;
- add a source-contract that forbids raw response-content JSON parsing in smoke;
- run a synthetic byte-array decoder check;
- if a kept 01-211 smoke stack is running, verify its public edge with the same
  decoding strategy without rebuilding the stack.

No application runtime or ingress behavior changes in E9.

## Status

Task remains `🟡` until the full Docker role/scale smoke is green.

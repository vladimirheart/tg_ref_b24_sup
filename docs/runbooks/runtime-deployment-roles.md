# Runtime deployment roles runbook

Task: `01-211`
Date: 2026-08-26

## Normal production roles

| Service | Runtime role | Replicas | Host ingress | Migration owner |
| --- | --- | ---: | --- | --- |
| `db-migrate` | `db-migrate` | 1 one-shot | no | yes |
| `ops-worker` | `ops-worker` | 1+ when `singletonWorkloads=[]` | no | no |
| `panel-web` | `panel-web` | 1+ | through nginx/panel-direct | no |

## What to inspect

Every backend process exposes:

```text
GET /actuator/info
```

Look at:

```json
{
  "iguanaRuntime": {
    "role": "web|worker|migrator",
    "instanceId": "...",
    "enabledWorkloads": [],
    "singletonWorkloads": []
  }
}
```

Before increasing worker replicas, `singletonWorkloads` must be empty.

## Duplicate/stuck job diagnostics

1. Check `APP_INSTANCE_ID` in logs/actuator.
2. Check Redis coordination availability.
3. For durable manual operations inspect `backend_ops_command`:
   - `status`
   - `claimed_by`
   - `claimed_at`
   - `heartbeat_at`
   - `attempt_count`
   - `last_error`
4. For leased schedulers check coordination lease logs.
5. Never solve duplicate execution by giving bot workers direct business JDBC access.

## Migration diagnostics

`panel-web` and `ops-worker` logs must contain a Flyway skip message for their runtime role.

Only `db-migrate` should execute Flyway/startup repair/bootstrap and exit `0`.

If `db-migrate` fails, web/worker must not be forced around it; fix the migration/startup failure and rerun.

## Replica operations

Increase replicas:

```powershell
.\scripts\docker-production-up.ps1 -WebReplicas 3 -WorkerReplicas 2
```

Decrease by rerunning with lower counts.

No hardcoded `container_name` is used, so Compose owns instance names.

## Restart semantics

- restarting one worker must not stop operator UI;
- restarting one web replica must not stop background workers;
- with 2+ web replicas nginx should keep serving through remaining healthy web peers.

## Multi-host warning

The current proof is single-host Compose. Shared config bind mounts are still a local-host boundary and must be externalized before multi-host orchestration.

## Verified scale smoke — 2026-08-26

Реальный Docker role/scale gate выполнен успешно на topology:

```text
db-migrate x1
panel-web x2
ops-worker x2
nginx x1
panel-direct x1
postgres + redis + rabbitmq + minio
```

Проверены role/workload isolation, single migration owner, отсутствие host ports у
`panel-web`/`ops-worker`, public routing только в WEB role, web-replica failover и
независимый restart worker при доступном UI.

Автоматический gate считается пройденным. До статуса `🟢` остаётся ручная
browser-проверка пользователем.

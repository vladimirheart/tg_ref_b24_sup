# Runtime deployment roles runbook

Tasks: `01-211`, `01-237`, `01-238`
Date: 2026-09-01

## Normal production roles

| Service | Runtime role | Replicas | Host ingress | Migration owner |
| --- | --- | ---: | --- | --- |
| `db-migrate` | `db-migrate` | 1 one-shot | no | yes |
| `ops-worker` | `ops-worker` | 1+ when `singletonWorkloads=[]` | no | no |
| `panel-web` | `panel-web` | 1+ | through nginx/panel-direct | no |
| `bot-runner` | `bot-runner` | exactly 1 | no | no |

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

`bot-runner` нельзя масштабировать. Он читает все active channels из PostgreSQL и запускает по одному дочернему runtime на канал; два supervisor создадут конкурирующий ingress для одного token.

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

Перезапуск только bot supervisor после изменения bot image или его runtime-конфига:

```powershell
docker compose -f docker-compose.production-contour.yml up -d --no-deps --force-recreate bot-runner
docker compose -f docker-compose.production-contour.yml logs --tail 200 bot-runner
```

Static Compose profiles `bot-telegram`, `bot-vk`, `bot-max` использовать только для изолированной аварийной диагностики. Перед их запуском остановите `bot-runner` или деактивируйте соответствующий канал, чтобы не получить duplicate Telegram long polling / `409 Conflict`.

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

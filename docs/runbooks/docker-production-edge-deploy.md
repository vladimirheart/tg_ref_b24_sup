# Iguana Docker Production Edge Deploy

Актуально на `26 августа 2026 года` после `01-211`.

## Назначение

Public nginx edge стоит только перед `panel-web`.

```text
Internet -> nginx -> panel-web x N
                    X ops-worker
```

`ops-worker` не является public upstream и не публикует host ports.

## Предпосылки

- base contour проходит `docker compose config`;
- `MONITORING_CREDENTIALS_MASTER_KEY` задан одинаково для всех backend roles;
- `IGUANA_PUBLIC_HOST` задан;
- при TLS существуют:
  - `deploy/nginx/certs/fullchain.pem`
  - `deploy/nginx/certs/privkey.pem`
- `spring-panel/run-windows.bat` не запущен: это local/dev launcher, а не production entrypoint.

При работающем Docker production-контуре launcher блокирует старт автоматически.
`IGUANA_ALLOW_LOCAL_PANEL_RUN=true` допустим только для намеренной изолированной диагностики вне production-контуров.

## Запуск

```powershell
.\scripts\docker-production-up.ps1 -Edge -ValidateOnly
.\scripts\docker-production-up.ps1 -Build -Edge -WebReplicas 2 -WorkerReplicas 2
```

```bash
./scripts/docker-production-up.sh --edge --validate-only
./scripts/docker-production-up.sh --build --edge --web-replicas 2 --worker-replicas 2
```

## Route matrix

- `/` -> `panel-web:8080`
- `/webhooks/max/<channel_id>` -> `panel-web:8080`
- `/webhooks/vk/<group_id>` -> `bot-vk:8080/callbacks/vk/<group_id>`
- `/internal/api/bot/**` -> `404`
- `/actuator/prometheus` -> `403`

Nginx upstream использует Docker DNS + `resolve`, поэтому scaled `panel-web` replicas могут обновляться без hardcoded container IP.

## Local fallback ingress

Base contour также содержит `panel-direct`, который публикует только loopback по умолчанию:

```text
http://127.0.0.1:8080
```

Это сохраняет local/admin access и не мешает `panel-web --scale`, потому что host port принадлежит proxy, а не web replicas.

## Smoke

Полная role/scale проверка:

```powershell
.\scripts\docker-production-role-smoke.ps1
```

Она использует отдельный project name, случайные host ports и отдельные Docker volumes, поэтому не должна смешиваться с production project.

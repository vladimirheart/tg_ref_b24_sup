# Docker Production Contour

Актуально на `26 августа 2026 года` после задачи `01-211`.

## 1. Топология

Canonical backend остаётся одним codebase/image, но запускается тремя явными deployment roles:

```text
db-migrate (one-shot)
        |
        v
ops-worker x M
        |
        +---- PostgreSQL
        +---- RabbitMQ
        +---- Redis
        +---- MinIO/S3
        ^
        |
panel-web x N
        |
        +---- panel-direct -> 127.0.0.1:8080
        |
        +---- optional nginx edge -> 80/443

bot-telegram / bot-vk / bot-max
        |
        +---- RabbitMQ + http://panel-web:8080 internal API
```

`panel-web`, `ops-worker` и `db-migrate` используют один `docker/panel.Dockerfile` и один image tag `IGUANA_PANEL_IMAGE`.

Это deployment split, а не microservice split: business data и schema остаются backend-owned.

## 2. Role ownership

- `db-migrate`:
  - `APP_RUNTIME_ROLE=db-migrate`;
  - единственный запускает Flyway/startup migration/repair/bootstrap workload;
  - `APP_RUNTIME_EXIT_AFTER_MIGRATION=true`;
  - должен завершиться с exit code `0`.
- `ops-worker`:
  - `APP_RUNTIME_ROLE=ops-worker`;
  - schedulers, broker consumers, monitoring, durable backend ops dispatcher;
  - не публикует host ports;
  - можно масштабировать только при отсутствии `SINGLETON` workload в `/actuator/info`.
- `panel-web`:
  - `APP_RUNTIME_ROLE=panel-web`;
  - operator UI/API/security/session и web-local SSE workload;
  - сам не публикует host port, поэтому `docker compose --scale panel-web=N` не конфликтует по `8080`.
- `panel-direct`:
  - lightweight nginx;
  - сохраняет исторический loopback URL `http://127.0.0.1:8080`;
  - проксирует только в `panel-web`.
- public `nginx` из `docker-compose.production-edge.yml`:
  - проксирует только в `panel-web`;
  - worker никогда не является ingress upstream.

## 3. Startup order

Compose фиксирует:

```text
postgres/rabbitmq/redis/minio
          |
          v
      db-migrate
          |
          v
      ops-worker
          |
          v
       panel-web
          |
          +--> panel-direct
          +--> optional nginx edge
```

Web/worker стартуют только после `db-migrate: service_completed_successfully`.

Дополнительные web/worker replicas Flyway не выполняют.

## 4. Scale

PowerShell:

```powershell
.\scripts\docker-production-up.ps1 -Build -WebReplicas 2 -WorkerReplicas 2
.\scripts\docker-production-up.ps1 -Build -Edge -WebReplicas 3 -WorkerReplicas 2
```

Linux/macOS shell:

```bash
./scripts/docker-production-up.sh --build --web-replicas 2 --worker-replicas 2
./scripts/docker-production-up.sh --build --edge --web-replicas 3 --worker-replicas 2
```

Defaults можно хранить в `.env`:

```text
IGUANA_PANEL_WEB_REPLICAS=1
IGUANA_OPS_WORKER_REPLICAS=1
```

Hardcoded `container_name` в contour отсутствуют, поэтому project name и `--scale` работают штатно.

## 5. Secrets

Split roles обязательно используют один и тот же:

```text
MONITORING_CREDENTIALS_MASTER_KEY
```

Он не должен меняться между `db-migrate`, `panel-web` и `ops-worker`.

Production helper scripts reject `change-me`.

Для fresh PostgreSQL deployment, если `ROLE_ADMIN` ещё нет, также задайте одновременно:

```text
APP_SECURITY_BOOTSTRAP_ADMIN_USERNAME
APP_SECURITY_BOOTSTRAP_ADMIN_PASSWORD
```

## 6. Internal discovery

Внутри Compose network используются только service DNS names:

- PostgreSQL -> `postgres:5432`
- RabbitMQ -> `rabbitmq:5672`
- Redis -> `redis:6379`
- S3 -> `minio:9000`
- bot internal API -> `panel-web:8080`

Host bind ports предназначены для operator/diagnostic access, а не для service-to-service routing.

## 7. Realtime and sessions

- HTTP sessions остаются JDBC-backed и доступны любому web replica.
- UI/SSE business events fanout выполняется через Redis.
- Каждый `panel-web` доставляет событие только своим process-local SSE clients.
- `APP_UI_EVENT_FANOUT_MODE=redis` задан compose явно.

## 8. Logs and instance identity

Container entrypoint по умолчанию задаёт:

```text
APP_INSTANCE_ID=$HOSTNAME
APP_PANEL_LOG_PATH=/opt/iguana/logs/spring-panel-<role>-<instance>.log
```

Поэтому scaled replicas не пишут в один и тот же file.

Runtime role/instance также доступны в `/actuator/info` и metrics tags.

## 9. Bot boundary

`bot-*`:

- остаются transport runtimes;
- не получают JDBC credentials business PostgreSQL;
- используют RabbitMQ и `http://panel-web:8080`;
- не являются source of truth business data.

## 10. Проверки

Static/targeted verification покрывает topology source contract.

Реальный isolated Docker smoke:

```powershell
.\scripts\docker-production-role-smoke.ps1
```

Smoke создаёт отдельный Compose project и временные volumes/ports, затем проверяет:

- `db-migrate x1` завершился `0`;
- `panel-web x2` healthy;
- `ops-worker x2` healthy;
- web не содержит worker dispatcher;
- worker содержит dispatcher и не содержит web SSE heartbeat;
- worker не содержит `SINGLETON` workloads;
- web/worker не публикуют host ports;
- nginx и panel-direct возвращают runtime role `web`;
- public edge блокирует `/internal/api/bot/**` и `/actuator/prometheus`;
- stop одного web replica оставляет edge доступным;
- restart worker не делает UI недоступным.

## 11. Границы Compose scaling

Этот контур доказывает single-host Docker Compose scale.

Перед multi-host orchestration потребуется отдельно externalize/проверить:

- shared config filesystem;
- persistent log aggregation;
- HA PostgreSQL/RabbitMQ/Redis/S3;
- external secret manager;
- orchestrator-native service discovery/readiness.

Это не повод создавать custom AuthService/FileService: service extraction по-прежнему должен следовать реальной ownership/scale/failure boundary.

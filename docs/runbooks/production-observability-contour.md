# Iguana production observability contour

Актуально на `2026-08-26`.

## Назначение

Этот runbook продолжает задачу `01-194` после deployment-role split `01-211`.
Application-side foundation (Actuator, Micrometer, Prometheus registry, SLI/SLO)
уже существовал; этот этап добавляет реальный Docker contour:

- Prometheus;
- Grafana;
- Alertmanager;
- Loki;
- Grafana Alloy;
- PostgreSQL exporter;
- Redis exporter;
- RabbitMQ built-in Prometheus endpoint;
- MinIO internal Prometheus endpoint.

`panel-web` и `ops-worker` продолжают использовать один backend image, но
Prometheus собирает каждый replica независимо через Docker DNS A-record discovery.
Метрики Spring уже содержат `runtime_role` и `instance_id`, поэтому replica-level
диагностика не зависит от container name.

## Версии, зафиксированные в overlay

На момент подготовки contour используются production release pins:

- Prometheus `v3.14.0`;
- Alertmanager `v0.34.0`;
- Grafana `13.2.0`;
- Loki `3.7.6`;
- Alloy `v1.18.0`;
- postgres_exporter `v0.20.1`;
- redis_exporter `v1.89.0`.

Обновлять их нужно отдельным проверяемым change-set, а не `latest` tags.

## Файлы

```text
docker-compose.production-observability.yml
observability/
  prometheus/
    prometheus.yml
    rules/iguana-alerts.yml
  alertmanager/alertmanager.yml
  loki/loki.yml
  alloy/config.alloy
  rabbitmq/enabled_plugins
  grafana/
    provisioning/
    dashboards/
scripts/docker-production-observability-smoke.ps1
```

## Запуск

PowerShell:

```powershell
.\scripts\docker-production-up.ps1 `
  -Observability `
  -WebReplicas 2 `
  -WorkerReplicas 2 `
  -Build
```

При необходимости edge и bot profiles добавляются теми же флагами, что и без
observability:

```powershell
.\scripts\docker-production-up.ps1 `
  -Observability -Edge -Telegram `
  -WebReplicas 2 -WorkerReplicas 2
```

Linux/macOS:

```bash
./scripts/docker-production-up.sh \
  --observability \
  --web-replicas 2 \
  --worker-replicas 2 \
  --build
```

## Обязательный secret

Для `-Observability` production preflight требует непустой и не-default:

```text
IGUANA_GRAFANA_ADMIN_PASSWORD
```

Пример есть в `.env.example`. Не хранить реальный пароль в Git.

## Локальные endpoints

По умолчанию всё bindится только на loopback:

```text
Grafana       http://127.0.0.1:3000
Prometheus    http://127.0.0.1:9090
Alertmanager  http://127.0.0.1:9093
Loki          http://127.0.0.1:3100
Alloy         http://127.0.0.1:12345
```

Не добавлять эти endpoints в public nginx без отдельной auth/network policy.
`/actuator/prometheus` также остаётся внутренним endpoint панели.

## Scrape topology

Prometheus использует `dns_sd_configs` для:

```text
panel-web:8080
ops-worker:8080
```

Это важно для scale-ready topology: static `panel-web:8080` target может скрыть
часть replicas, тогда как DNS discovery materializes каждый A record как
отдельный target.

Infrastructure targets:

```text
postgres-exporter:9187
redis-exporter:9121
rabbitmq:15692
minio:9000/minio/v2/metrics/cluster
```

RabbitMQ Prometheus plugin включается через versioned `enabled_plugins` file.
MinIO metrics переводятся в `public` auth mode только внутри production Docker
boundary. Поэтому `APP_STORAGE_OBJECT_BIND_HOST` должен оставаться loopback/internal;
не публиковать metrics endpoint в интернет.

## Alerts

Versioned rules находятся в:

```text
observability/prometheus/rules/iguana-alerts.yml
```

Начальный набор покрывает:

- WEB/WORKER scrape loss;
- Iguana production readiness;
- readiness refresh failure;
- DLQ backlog;
- failed/stale incident delivery;
- HTTP p95 > 2s;
- 5xx ratio > 2%;
- PostgreSQL/Redis/RabbitMQ/MinIO metrics availability;
- Loki/Alloy availability.

Alertmanager в этом slice является durable aggregator/state owner. Его default
receiver намеренно не содержит нового внешнего webhook/SMTP secret. Реальную
наружную доставку нужно подключать через уже утвержденный Iguana notification
boundary, а не заводить ad-hoc credential в infrastructure YAML.

## Logs

Alloy читает versioned application logs из общего `IGUANA_LOGS_DIR` bind mount и
пишет их в Loki. Это покрывает:

- `spring-panel-panel-web-<instance>.log`;
- `spring-panel-ops-worker-<instance>.log`;
- bot log files;
- другие `.log` файлы Iguana в общей logs directory.

В этом slice Alloy **не получает Docker socket**. Это намеренное security решение:
read-only bind Unix socket не делает Docker API read-only и фактически расширяет
host-control boundary. Infrastructure container stdout/stderr можно добавить
отдельно через безопасный socket proxy/syslog/daemon-level log shipping, если
это станет эксплуатационной необходимостью.

## Grafana provisioning

Автоматически создаются datasources:

- `Prometheus` (`uid=prometheus`);
- `Loki` (`uid=loki`).

И dashboards:

- `Iguana Runtime Overview`;
- `Iguana Infrastructure`;
- `Iguana Logs`.

## Smoke

После запуска:

```powershell
.\scripts\docker-production-observability-smoke.ps1
```

Smoke проверяет:

- compose config;
- readiness Prometheus / Alertmanager / Loki / Alloy / Grafana;
- Prometheus active targets для WEB, WORKER, PostgreSQL, Redis, RabbitMQ, MinIO;
- наличие minimum one healthy WEB target and one healthy WORKER target;
- Grafana health API.

До реального Docker smoke задача `01-194` остаётся `🟡`.
После automated runtime verification AI может поставить только `🟣`.
`🟢` — только после ручной проверки пользователя.

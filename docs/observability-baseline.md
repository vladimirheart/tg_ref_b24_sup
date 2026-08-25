# Iguana Observability Baseline

Актуально на `2026-08-25`.

## 1. Назначение

Этот документ фиксирует минимальный production-ready observability baseline для Iguana. Его цель:

- дать стандартные health/metrics surfaces для `spring-panel`;
- определить минимальные SLI/SLO;
- зафиксировать стартовый набор алертов;
- описать, как подключать `Prometheus` и `Grafana`;
- не смешивать прикладной monitoring Iguana с базовой системной телеметрией.

## 2. Что уже включено в `spring-panel`

Начиная с этого baseline, `spring-panel` поднимает стандартный observability stack:

- Spring Boot Actuator;
- Micrometer;
- Prometheus registry;
- built-in health indicators для:
  - `db`
  - `diskSpace`
  - `ping`
  - `rabbit`
  - `redis`
- custom health indicator `iguanaProduction`, который опирается на production readiness snapshot;
- custom gauges по последнему readiness snapshot Iguana.

## 3. Доступные endpoints

По умолчанию используются:

- `/actuator/health`
- `/actuator/health/liveness`
- `/actuator/health/readiness`
- `/actuator/info`
- `/actuator/metrics`
- `/actuator/prometheus`

Практический смысл:

- `health` и `health/*` нужны для оркестрации, reverse proxy и базового external probing;
- `metrics` полезен для ручной диагностики и проверки названий meter'ов;
- `prometheus` нужен для scrape мониторингом.

## 4. Важное ограничение по безопасности

`/actuator/prometheus` не должен публиковаться в открытый интернет.

Обязательное правило:

- endpoint должен быть доступен только из внутренней сети, service mesh, VPN или через reverse proxy с network restrictions.

Минимально допустимый production вариант:

- `spring-panel` слушает internal interface;
- Prometheus scraper ходит по внутреннему адресу;
- внешний ingress не публикует `/actuator/prometheus`.

## 5. Production-specific метрики Iguana

### 5.1. Readiness gauges

- `iguana.production.readiness`
  - `1`, если последний readiness snapshot = `ready`
  - `0` в остальных случаях
- `iguana.production.readiness.refresh_success`
  - `1`, если последняя refresh-итерация snapshot cache успешна
  - `0`, если readiness metrics refresh не удалась

### 5.2. Component gauges

Метрика:

- `iguana.production.component.ready{component=...}`

Компоненты:

- `postgresql`
- `redis`
- `rabbitmq`
- `object_storage`
- `incident_delivery`

Значение:

- `1`, если компонент в последнем snapshot healthy
- `0`, если degraded/unavailable/compatibility

### 5.3. Queue gauges

Метрика:

- `iguana.transport.queue.messages{queue_metric=...}`

Теги `queue_metric`:

- `inbound_messages`
- `ticket_created_messages`
- `inbound_dlq_messages`
- `ticket_created_dlq_messages`

### 5.4. Incident delivery gauges

Метрика:

- `iguana.incident.delivery.outbox{metric=...}`

Теги `metric`:

- `failed_current`
- `queued_current`
- `processing_current`
- `stale_processing`
- `delivered_24h`
- `failed_24h`

## 6. Что даёт Micrometer из коробки

После включения baseline дополнительно доступны стандартные метрики Spring Boot:

- `http.server.requests`
- JVM memory/GC/threads/classes
- process uptime
- system CPU/load
- logback metrics
- Tomcat metrics
- datasource/connection pool metrics, если их публикуют используемые компоненты

Это значит, что даже без кастомной бизнес-аналитики уже можно строить базовые production dashboards.

## 7. Минимальный Prometheus scrape

Пример стартовой конфигурации:

```yaml
scrape_configs:
  - job_name: iguana-spring-panel
    metrics_path: /actuator/prometheus
    scrape_interval: 15s
    static_configs:
      - targets:
          - panel.internal.example:8080
```

Если используется reverse proxy или service discovery, адаптируйте target path под ваш deployment.

## 8. Минимальный Grafana dashboard набор

Для первого production запуска достаточно 4 дашбордов:

### 8.1. Platform health

- panel up/down
- JVM heap
- GC pause
- CPU
- threads
- uptime

### 8.2. HTTP/API

- request rate
- `5xx` rate
- `4xx` rate
- p95 latency
- top slow endpoints

### 8.3. Iguana production readiness

- `iguana.production.readiness`
- component status по `postgresql/redis/rabbitmq/object_storage/incident_delivery`
- readiness refresh success

### 8.4. Transport and incidents

- queue depth
- DLQ depth
- incident delivery failed/stale
- backlog по incident delivery outbox

## 9. Минимальные SLI

Для первого production среза рекомендуются такие SLI:

### 9.1. Availability

- panel HTTP availability;
- readiness availability;
- internal bot API availability.

### 9.2. Latency

- p95 `http.server.requests` для operator-facing запросов;
- p95 времени открытия диалога;
- p95 времени ответа на operator reply API.

### 9.3. Reliability

- доля `5xx` ответов;
- DLQ backlog;
- failed incident delivery;
- stale transport processing.

### 9.4. Business-facing runtime health

- production readiness overall;
- delivery success по transport contour;
- queue age / queue accumulation;
- доля обращений, дошедших до оператора без ручного replay.

## 10. Стартовые SLO

Это не жёсткая истина, а безопасный начальный baseline для вашего текущего масштаба:

- Panel availability: `99.5%` за месяц.
- Readiness availability: `99.0%` за месяц.
- Operator-facing p95 HTTP latency: `< 2s` на 15-минутном окне.
- `5xx` error rate: `< 2%` на 5-минутном окне.
- `DLQ > 0` дольше `10 минут` считается alert condition.
- `incident delivery failed_current > 0` дольше `10 минут` считается alert condition.
- `stale_processing > 0` дольше `5 минут` считается alert condition.

Для более зрелого production эти цели нужно будет уточнять по фактической статистике.

## 11. Минимальный alerting baseline

### 11.1. Critical

- `iguana.production.readiness == 0` более `5 минут`
- `up == 0` для panel target
- RabbitMQ target unavailable
- PostgreSQL target unavailable
- object storage probe unavailable

### 11.2. High

- `rate(http_server_requests_seconds_count{status=~"5.."}[5m])` выше внутреннего порога
- `iguana.transport.queue.messages{queue_metric=~".*_dlq_messages"} > 0` более `10 минут`
- `iguana.incident.delivery.outbox{metric="failed_current"} > 0`
- `iguana.incident.delivery.outbox{metric="stale_processing"} > 0`

### 11.3. Medium

- p95 latency выше `2s`
- heap pressure стабильно высокая
- disk usage приближается к лимиту
- readiness refresh success = `0`

## 12. Что не покрывает этот baseline

Этот baseline не заменяет:

- прикладной monitoring Iguana по SSL/RMS/iiko и другим support-системам;
- бизнес-аналитику операторов;
- глубокий tracing;
- отдельный лог-агрегатор;
- внешний synthetic monitoring.

Это только обязательный первый слой production observability.

## 13. Как использовать вместе с другими документами

- За production launch отвечать вместе с [production-launch-checklist.md](./runbooks/production-launch-checklist.md).
- За production contour и runtime boundaries отвечать вместе с [postgresql-production-contour.md](./runbooks/postgresql-production-contour.md).
- За bot runtime contract отвечать вместе с [BOT_RUNTIME_CONTRACT.md](./BOT_RUNTIME_CONTRACT.md).

## 14. Следующие шаги после baseline

- вынести scrape/alert rules в versioned infra-конфигурацию;
- добавить дашборды как артефакты репозитория;
- расширить SLI до operator/business уровня;
- добавить monitoring coverage внешних support-систем;
- внедрить log aggregation и correlation с incident workbench.

# 01-194 — production observability contour Phase 2

- Время: `2026-08-26 14:21 +03:00`
- Задача: `01-194`
- Области: Docker observability overlay, Prometheus/Grafana/Alertmanager/Loki/Alloy, exporters, alerts, dashboards, helpers, smoke, runbook

## Промт пользователя

```text
хорошо. перевёл 01-211 в 🟢 но пока не пушал изменения.
сделай все последующие необходимые задачи и приступай к выполнению задачи следующей по логике, например недоделанную 01-194 и\или 01-198
```

## Причина продолжения

Предыдущий `01-194` закрыл application-side foundation: Actuator, Micrometer,
Prometheus registry, health surfaces, SLI/SLO и документацию. Исходный
архитектурный roadmap после `01-211` требует реального production observability
contour, поэтому задача повторно переводится в `🟡` и продолжается Phase 2.

## Что реализовано в static change-set

- `docker-compose.production-observability.yml` с pinned Prometheus,
  Alertmanager, Grafana, Loki, Alloy, postgres_exporter и redis_exporter;
- RabbitMQ built-in Prometheus plugin и internal MinIO metrics;
- DNS service discovery отдельных `panel-web` / `ops-worker` replicas;
- versioned Prometheus alert rules по readiness, DLQ, delivery, HTTP SLO и infra;
- Grafana provisioning для Prometheus/Loki и три Iguana dashboard;
- Loki filesystem/TSDB single-node contour и Alloy file-log shipping без raw
  Docker socket;
- helper-script contract `-Observability` / `--observability`;
- real Docker observability smoke и source-contract test;
- отдельный runbook и `.env.example` contract.

До реального Docker smoke `01-194` остаётся `🟡`. AI не переводит её в `🟢`.

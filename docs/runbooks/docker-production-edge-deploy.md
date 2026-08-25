# Iguana Docker Production Edge Deploy

Актуально на `25 августа 2026 года`.

## 1. Назначение

Этот runbook описывает, как поверх базового dockerized production contour Iguana подключать public ingress через `nginx`.

Он нужен для сценария, когда:

- `spring-panel` и внутренние infra ports не хочется публиковать наружу напрямую;
- нужен домен для operator UI;
- нужен публичный `VK` callback URL;
- нужен TLS termination без отдельной ручной конфигурации вне репозитория.

Базовый контур, на который опирается этот слой:

- [docker-compose.production-contour.yml](../../docker-compose.production-contour.yml)
- [docker-compose.production-edge.yml](../../docker-compose.production-edge.yml)
- [docker-production-contour.md](../docker-production-contour.md)
- [production-launch-checklist.md](./production-launch-checklist.md)

## 2. Что именно делает edge layer

`docker-compose.production-edge.yml` добавляет контейнер `nginx`, который:

- проксирует основной UI и backend traffic в `spring-panel`;
- публикует `VK` callback URL в виде `/webhooks/vk/<group_id>` и делает rewrite на `bot-vk`;
- оставляет `MAX` webhook path на стороне `spring-panel`, где уже живёт relay-controller;
- не публикует наружу `/internal/api/bot/**`;
- не публикует наружу `/actuator/prometheus`.

## 3. Обязательные предпосылки

- Базовый contour уже собран и проходит preflight.
- В `.env` задан `IGUANA_PUBLIC_HOST`.
- Если нужен TLS, подготовлены файлы:
  - `deploy/nginx/certs/fullchain.pem`
  - `deploy/nginx/certs/privkey.pem`
- Для `VK` реально включён профиль `vk`, если нужен callback ingress.

## 4. Рекомендуемый env baseline

```text
APP_PANEL_BIND_HOST=127.0.0.1
APP_POSTGRES_BIND_HOST=127.0.0.1
APP_RABBITMQ_AMQP_BIND_HOST=127.0.0.1
APP_RABBITMQ_HTTP_BIND_HOST=127.0.0.1
APP_REDIS_BIND_HOST=127.0.0.1
APP_STORAGE_OBJECT_BIND_HOST=127.0.0.1
APP_STORAGE_OBJECT_CONSOLE_BIND_HOST=127.0.0.1
VK_BOT_BIND_HOST=127.0.0.1
MAX_BOT_BIND_HOST=127.0.0.1
IGUANA_PUBLIC_HOST=support.example.com
IGUANA_EDGE_HTTP_BIND_HOST=0.0.0.0
IGUANA_EDGE_HTTP_PORT=80
IGUANA_EDGE_HTTPS_BIND_HOST=0.0.0.0
IGUANA_EDGE_HTTPS_PORT=443
IGUANA_EDGE_TLS_ENABLED=true
IGUANA_EDGE_CERTS_DIR=./deploy/nginx/certs
```

Практический смысл:

- наружу открывается только edge ingress;
- внутренние runtime и infra ports остаются loopback-only;
- panel корректно получает `X-Forwarded-*` headers от reverse proxy.

## 5. Как запускать

Проверка без фактического старта:

```powershell
.\scripts\docker-production-up.ps1 -Edge -ValidateOnly
```

```bash
./scripts/docker-production-up.sh --edge --validate-only
```

Боевой запуск edge contour:

```powershell
.\scripts\docker-production-up.ps1 -Build -Edge
.\scripts\docker-production-up.ps1 -Build -Edge -Telegram -Vk
```

```bash
./scripts/docker-production-up.sh --build --edge
./scripts/docker-production-up.sh --build --edge --telegram --vk
```

Прямой `docker compose` вариант:

```bash
docker compose \
  -f docker-compose.production-contour.yml \
  -f docker-compose.production-edge.yml \
  --profile vk \
  up -d --build
```

Остановка:

```powershell
.\scripts\docker-production-down.ps1 -Edge
```

```bash
./scripts/docker-production-down.sh --edge
```

## 6. Route matrix

- `/` -> `spring-panel:8080`
- `/webhooks/max/<channel_id>` -> `spring-panel:8080`
- `/webhooks/vk/<group_id>` -> rewrite на `bot-vk:8080/callbacks/vk/<group_id>`

Что сознательно не публикуется наружу:

- `/internal/api/bot/**`
- `/actuator/prometheus`

## 7. Preflight и fail-fast checks

Helper-скрипты при `-Edge` или `--edge` дополнительно проверяют:

- задан ли `IGUANA_PUBLIC_HOST`;
- если `IGUANA_EDGE_TLS_ENABLED=true`, существуют ли `fullchain.pem` и `privkey.pem`;
- не остался ли `IGUANA_PUBLIC_HOST=localhost` в production-like режиме без `AllowInsecureDefaults`.

Это сделано специально, чтобы edge contour не собирался в полубоевом и двусмысленном состоянии.

## 8. Минимальный smoke после запуска

1. Открыть `http://<domain>` или `https://<domain>`.
2. Проверить логин в panel.
3. Проверить, что `/nginx-health` отвечает `200`.
4. Проверить, что `/actuator/prometheus` извне не публикуется.
5. Если включён `vk`, отправить тестовый callback в `https://<domain>/webhooks/vk/<group_id>`.
6. Если включён `max`, проверить webhook URL `https://<domain>/webhooks/max/<channel_id>`.

## 9. Практические границы этого решения

Этот edge layer закрывает первый production-like ingress scenario, но не претендует на финальную platform maturity.

Что всё ещё остаётся следующим слоем:

- cert renewal automation;
- WAF/rate-limiting;
- dedicated observability stack;
- external secrets backend;
- multi-host orchestration.

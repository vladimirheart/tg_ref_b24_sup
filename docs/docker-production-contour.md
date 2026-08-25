# Docker Production Contour

Документ фиксирует рекомендуемый dockerized contour Iguana на `25 августа 2026 года`.

## 1. Что именно добавлено

В репозитории появился отдельный compose-контур [docker-compose.production-contour.yml](../docker-compose.production-contour.yml) с сервисами:

- `spring-panel`;
- `postgres`;
- `rabbitmq`;
- `redis`;
- `minio`;
- `bot-telegram`;
- `bot-vk`;
- `bot-max`.

Также добавлены:

- [docker/panel.Dockerfile](../docker/panel.Dockerfile);
- [docker/bot.Dockerfile](../docker/bot.Dockerfile);
- [.dockerignore](../.dockerignore).

## 2. Практический смысл контура

Этот contour соответствует уже зафиксированному target-state проекта:

- `spring-panel` остаётся canonical backend-owner;
- `java-bot` живёт как отдельный transport runtime;
- `PostgreSQL + RabbitMQ + Redis + S3-compatible storage` собираются как обязательный runtime foundation;
- bot runtimes стартуют отдельными контейнерами, а не как child-process внутри panel.

Это важнее, чем преждевременно дробить `spring-panel` на несколько доменных микросервисов.

## 3. Что входит в обязательный docker baseline

- `postgres` — canonical business/runtime DB.
- `rabbitmq` — transport backbone.
- `redis` — coordination, leases, shared cooldown/cursor semantics.
- `minio` — локальный S3-compatible storage boundary.
- `spring-panel` — backend-owner и operator UI.
- `bot-*` — отдельные transport workers по каналам.

## 4. Что сознательно не включено в этот slice

Следующие вещи остаются следующим этапом, а не базовым docker bootstrap:

- reverse proxy (`nginx` / `traefik`);
- TLS termination;
- `Prometheus` / `Grafana`;
- внешний secret manager;
- multi-host orchestration (`Swarm`, `Kubernetes`, `Nomad`).

Для текущего проекта это правильнее вести как отдельный infra-hardening scope, а не смешивать с первым docker contour.

## 5. Важный operational invariant

В containerized contour panel не должна auto-start'ить bot child processes внутри собственного контейнера.

Для этого добавлен флаг:

- `app.bots.auto-start-enabled`;
- env alias: `APP_BOT_AUTO_START_ENABLED`.

В compose он уже установлен в `false`.

Это оставляет legacy/dev path рабочим по умолчанию, но делает docker deployment честным:

- bot containers живут отдельно;
- panel не пытается быть process supervisor для внешнего contour.

При этом panel image всё равно содержит рядом `java-bot/` tree в read-only роли:

- чтобы `status` / `runtime-contract` не падали на отсутствии каталога;
- чтобы compatibility API оставался диагностируемым;
- но не для того, чтобы panel управляла production deployment бот-контейнеров.

## 6. Как запускать

Минимальный bootstrap:

```bash
docker compose -f docker-compose.production-contour.yml up -d --build
```

Или через штатные скрипты репозитория:

```powershell
.\scripts\docker-production-up.ps1 -Build
.\scripts\docker-production-up.ps1 -Build -Telegram
.\scripts\docker-production-up.ps1 -Build -Telegram -Vk
```

```bash
./scripts/docker-production-up.sh --build
./scripts/docker-production-up.sh --build --telegram
./scripts/docker-production-up.sh --build --telegram --vk
```

С конкретными каналами:

```bash
docker compose -f docker-compose.production-contour.yml --profile telegram up -d --build
docker compose -f docker-compose.production-contour.yml --profile vk up -d --build
docker compose -f docker-compose.production-contour.yml --profile max up -d --build
```

Комбинированный запуск:

```bash
docker compose -f docker-compose.production-contour.yml \
  --profile telegram \
  --profile vk \
  up -d --build
```

Остановка:

```powershell
.\scripts\docker-production-down.ps1
.\scripts\docker-production-down.ps1 -RemoveVolumes
```

```bash
./scripts/docker-production-down.sh
./scripts/docker-production-down.sh --remove-volumes
```

## 7. Какие директории монтируются

Compose использует bind mounts, чтобы не ломать текущий data ownership проекта:

- `./config/shared` -> `/opt/iguana/config/shared`
- `./attachments` -> `/opt/iguana/attachments`
- `./logs` -> `/opt/iguana/logs`
- `./bot_databases` -> `/opt/iguana/bot_databases`

При необходимости их можно переопределить через:

- `IGUANA_SHARED_CONFIG_DIR`
- `IGUANA_ATTACHMENTS_DIR`
- `IGUANA_LOGS_DIR`
- `IGUANA_BOT_DATABASES_DIR`

## 8. Какие env нужно задать обязательно

Минимум для panel:

- `APP_INTERNAL_BOT_API_TOKEN`
- `APP_SECURITY_REMEMBER_ME_KEY`
- `APP_SECURITY_BOOTSTRAP_ADMIN_USERNAME`
- `APP_SECURITY_BOOTSTRAP_ADMIN_PASSWORD`

Минимум для infra:

- `IGUANA_POSTGRES_PASSWORD`
- `IGUANA_RABBITMQ_PASSWORD`
- `IGUANA_REDIS_PASSWORD`
- `APP_STORAGE_OBJECT_ACCESS_KEY`
- `APP_STORAGE_OBJECT_SECRET_KEY`
- `APP_STORAGE_OBJECT_BUCKET`

Минимум для каналов:

- `TELEGRAM_BOT_TOKEN`
- `TELEGRAM_BOT_USERNAME`
- `GROUP_CHAT_ID`
- `VK_BOT_TOKEN`
- `VK_GROUP_ID`
- `VK_OPERATOR_CHAT_ID`
- `MAX_BOT_TOKEN`
- `MAX_CHANNEL_ID`
- `MAX_SUPPORT_CHAT_ID`

## 9. Как читать этот contour

Новый compose-файл нужно понимать так:

- это production-like container baseline для текущего репозитория;
- это не финальная distributed platform;
- это не сигнал к немедленному распилу `spring-panel` на микросервисы.
- lifecycle bot containers в этом contour принадлежит `docker compose`, а не panel-side `Start/Stop`.

Правильная следующая эволюция после этого шага:

1. Обкатать `spring-panel + infra + external bot containers`.
2. Добавить reverse proxy и observability stack.
3. Только после этого решать, нужен ли отдельный `integration-worker` или иной service split.

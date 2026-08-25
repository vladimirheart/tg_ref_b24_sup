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
- [docker-compose.production-edge.yml](../docker-compose.production-edge.yml);
- [docker/nginx/templates/http-only.conf.template](../docker/nginx/templates/http-only.conf.template);
- [docker/nginx/templates/tls.conf.template](../docker/nginx/templates/tls.conf.template);
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

Следующие вещи не входят в mandatory bootstrap самого base contour и подключаются отдельным слоем:

- reverse proxy / public ingress через [docker-compose.production-edge.yml](../docker-compose.production-edge.yml);
- TLS termination через тот же `nginx` edge override;
- `Prometheus` / `Grafana`;
- внешний secret manager;
- multi-host orchestration (`Swarm`, `Kubernetes`, `Nomad`).

Для текущего проекта это правильнее вести как layered contour:

- base stack отвечает за business/runtime foundation;
- edge stack отвечает за public ingress и TLS;
- observability и secret-management остаются отдельным infra-hardening scope.

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

Для contour с публичным ingress через `nginx`:

```bash
docker compose \
  -f docker-compose.production-contour.yml \
  -f docker-compose.production-edge.yml \
  up -d --build
```

Перед первым запуском обычно нужно подготовить `.env` на основе шаблона:

```bash
cp .env.example .env
```

```powershell
Copy-Item .env.example .env
```

Или через штатные скрипты репозитория:

```powershell
.\scripts\docker-production-up.ps1 -Build
.\scripts\docker-production-up.ps1 -Build -Edge
.\scripts\docker-production-up.ps1 -Build -Telegram
.\scripts\docker-production-up.ps1 -Build -Telegram -Vk
.\scripts\docker-production-up.ps1 -Build -Edge -Telegram -Vk
.\scripts\docker-production-up.ps1 -ValidateOnly
```

```bash
./scripts/docker-production-up.sh --build
./scripts/docker-production-up.sh --build --edge
./scripts/docker-production-up.sh --build --telegram
./scripts/docker-production-up.sh --build --telegram --vk
./scripts/docker-production-up.sh --build --edge --telegram --vk
./scripts/docker-production-up.sh --validate-only
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
.\scripts\docker-production-down.ps1 -Edge
.\scripts\docker-production-down.ps1 -RemoveVolumes
```

```bash
./scripts/docker-production-down.sh
./scripts/docker-production-down.sh --edge
./scripts/docker-production-down.sh --remove-volumes
```

## 6.1. Edge layer и public ingress

`docker-compose.production-edge.yml` добавляет `nginx` как отдельный ingress layer поверх base stack.

Он нужен, когда нужно:

- вынести наружу только `80/443`, а не внутренние service ports;
- публиковать panel по доменному имени;
- держать `VK` webhook path на публичном URL `/webhooks/vk/<group_id>` с rewrite на bot runtime;
- завершать TLS без встраивания web-server логики в `spring-panel`.

Практические инварианты:

- внутренние publish-порты base contour по умолчанию привязаны к `127.0.0.1`;
- `spring-panel` получает `X-Forwarded-*` headers и работает с `SERVER_FORWARD_HEADERS_STRATEGY=framework`;
- `/actuator/prometheus` не должен публиковаться через внешний ingress;
- `/internal/api/bot/**` не должен открываться наружу через reverse proxy.

Подробный deploy-порядок вынесен в [docs/runbooks/docker-production-edge-deploy.md](./runbooks/docker-production-edge-deploy.md).

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

Для edge layer дополнительно используется bind mount:

- `./deploy/nginx/certs` -> `/etc/nginx/certs`

Его можно переопределить через `IGUANA_EDGE_CERTS_DIR`.

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

Минимум для edge contour:

- `IGUANA_PUBLIC_HOST`
- `IGUANA_EDGE_TLS_ENABLED`
- `IGUANA_EDGE_CERTS_DIR` и файлы `fullchain.pem` / `privkey.pem`, если TLS включён

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

Helper-скрипты перед запуском делают preflight-проверку:

- наличие `config/shared/settings.json`, `locations.json`, `org_structure.json`;
- наличие обязательных infra secrets;
- отсутствие встроенных insecure defaults для production-like contour;
- наличие channel credentials для включённых профилей;
- для `-Edge` ещё и наличие `IGUANA_PUBLIC_HOST`;
- для `-Edge` с `IGUANA_EDGE_TLS_ENABLED=true` ещё и наличие `deploy/nginx/certs/fullchain.pem` и `privkey.pem`.

Если `.env` ещё не подготовлен и нужные переменные не заданы в process environment, helper-скрипт завершится fail-fast с подсказкой, какой именно ключ отсутствует.

Если нужен именно локальный совместимый contour с временно небезопасными дефолтами, это надо делать явно:

```powershell
.\scripts\docker-production-up.ps1 -Build -AllowInsecureDefaults
```

```bash
./scripts/docker-production-up.sh --build --allow-insecure-defaults
```

Точно так же для локального edge contour с `localhost` и без боевых TLS-сертификатов:

```powershell
.\scripts\docker-production-up.ps1 -Build -Edge -AllowInsecureDefaults
```

```bash
./scripts/docker-production-up.sh --build --edge --allow-insecure-defaults
```

## 9. Как читать этот contour

Новый compose-файл нужно понимать так:

- это production-like container baseline для текущего репозитория;
- это не финальная distributed platform;
- это не сигнал к немедленному распилу `spring-panel` на микросервисы.
- lifecycle bot containers в этом contour принадлежит `docker compose`, а не panel-side `Start/Stop`.
- public ingress и TLS лучше подключать отдельным compose override, а не открывать наружу все внутренние порты.

Правильная следующая эволюция после этого шага:

1. Обкатать `spring-panel + infra + external bot containers`.
2. Подключить `nginx` edge layer и проверить домен/TLS/webhook ingress.
3. Добавить observability stack.
4. Только после этого решать, нужен ли отдельный `integration-worker` или иной service split.

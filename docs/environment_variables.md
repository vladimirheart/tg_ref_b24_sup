# 🌍 Переменные окружения

Ниже перечислены основные переменные, используемые Java-панелью и Java-ботом.

## Базовые переменные

| Переменная | Описание | Где используется |
| --- | --- | --- |
| `TELEGRAM_BOT_TOKEN` | токен Telegram-бота | Java-бот |
| `TELEGRAM_BOT_USERNAME` | @username бота | Java-бот |
| `GROUP_CHAT_ID` | ID рабочей группы/чата | Java-бот |
| `VK_BOT_ENABLED` | включить VK-бота (`true/false`) | Java-бот |
| `VK_BOT_TOKEN` | токен VK | Java-бот |
| `VK_GROUP_ID` | ID сообщества VK | Java-бот |
| `VK_OPERATOR_CHAT_ID` | чат операторов VK | Java-бот |
| `MAX_BOT_ENABLED` | включить MAX-бота (`true/false`) | Java-бот |
| `MAX_BOT_TOKEN` | токен MAX | Java-бот |
| `MAX_SUPPORT_CHAT_ID` | чат операторов MAX | Java-бот |
| `APP_DB_MODE` | режим БД: normal runtime default `postgresql`; `sqlite` только явный compatibility override; для панели ещё `mysql`, `auto` допустим только как ручной transitional режим | Панель и бот |
| `DATABASE_URL` | compatibility shorthand для external DB; для `java-bot` поддержан только PostgreSQL | Панель и бот |
| `SPRING_DATASOURCE_URL` | явный JDBC URL для external DB | Панель и бот |
| `SPRING_DATASOURCE_USERNAME` | пользователь external DB | Панель и бот |
| `SPRING_DATASOURCE_PASSWORD` | пароль external DB | Панель и бот |
| `IGUANA_BOOTSTRAP_DB_MODE` | режим first-run bootstrap: `auto` или `postgresql`; normal path всегда должен вести в PostgreSQL/RabbitMQ | bootstrap scripts |
| `APP_POSTGRES_PORT` | локальный порт для dockerized PostgreSQL bootstrap | bootstrap scripts |
| `IGUANA_BOOTSTRAP_INSTALL_DOCKER` | разрешить Windows bootstrap автоматически поставить Docker Desktop через `winget` | bootstrap scripts |
| `IGUANA_BOOTSTRAP_DOCKER_READY_TIMEOUT_SECONDS` | timeout ожидания готовности Docker Desktop после установки/старта | bootstrap scripts |
| `APP_INTEGRATION_TRANSPORT_MODE` | transport boundary для integration runtime: `jdbc` только compatibility/dev path, `rabbitmq` для live contour | Java-бот |
| `APP_PANEL_INTERNAL_API_BASE_URL` | base URL internal panel API для bot-side live reads/writes в `rabbitmq` contour | Java-бот |
| `APP_PANEL_INTERNAL_API_TOKEN` | токен internal panel API для bot-side live reads/writes в `rabbitmq` contour | Java-бот |
| `APP_PANEL_INTERNAL_API_REQUEST_SIGNING_ENABLED` | включает signed headers (`timestamp` + `HMAC`) для bot -> panel internal API | Java-бот |
| `APP_PANEL_INTERNAL_API_SIGNATURE_SECRET` | выделенный shared secret для подписи internal bot API; если пусто, bot и panel fallback-ятся к токену | Панель и Java-бот |
| `APP_PANEL_INTERNAL_API_REQUEST_TIMEOUT` | timeout одного bot -> panel internal API запроса | Java-бот |
| `APP_PANEL_INTERNAL_API_RETRY_ATTEMPTS` | количество retry попыток bot-side write-запросов к internal panel API | Java-бот |
| `APP_PANEL_INTERNAL_API_RETRY_BACKOFF` | базовый backoff между retry попытками bot-side write-запросов | Java-бот |
| `APP_INTERNAL_BOT_API_TOKEN` | токен internal panel bot API на стороне `spring-panel`; во внешнем production-like контуре должен быть явно переопределён и не может оставаться дефолтным | Панель |
| `APP_INTERNAL_BOT_API_SIGNATURE_SECRET` | shared secret для проверки `X-Iguana-Request-Signature` на стороне `spring-panel` | Панель |
| `APP_INTERNAL_BOT_API_REQUIRE_REQUEST_SIGNATURE` | требовать signed internal bot API requests на стороне `spring-panel` | Панель |
| `APP_INTERNAL_BOT_API_REQUEST_TIMESTAMP_SKEW` | допустимый clock skew для signed internal bot API requests | Панель |
| `APP_INTERNAL_BOT_API_IDEMPOTENCY_INFLIGHT_TTL` | TTL claim-состояния для in-flight idempotency key на internal bot API | Панель |
| `APP_INTERNAL_BOT_API_IDEMPOTENCY_TTL` | TTL cached-response слоя для replay-safe idempotency write-запросов | Панель |
| `APP_COORDINATION_MODE` | coordination backend: `direct` для local/dev, `redis` для shared leases/counters/cooldowns в production contour | Панель и бот |
| `APP_COORDINATION_LEASE_NAMESPACE` | namespace ключей coordination lease/counter/cooldown в Redis | Панель и бот |
| `APP_COORDINATION_BOT_INGRESS_LEASE_TTL` | TTL ingress lease для bot long-poll owner semantics | Java-бот |
| `APP_COORDINATION_BOT_INGRESS_RENEW_INTERVAL` | интервал продления ingress lease для bot long-poll owner semantics | Java-бот |
| `APP_COORDINATION_BOT_INGRESS_FOLLOWER_BACKOFF` | задержка follower bot instance перед повторной попыткой захватить ingress lease | Java-бот |
| `APP_COORDINATION_BOT_JOB_LEASE_TTL` | TTL distributed lease для bot-side scheduled jobs (`unblock digest`, `session expiry`) | Java-бот |
| `APP_COORDINATION_BOT_SESSION_TTL` | TTL shared bot session snapshot в Redis/local session store | Java-бот |
| `APP_BOT_AUTO_START_ENABLED` | включает panel-side auto-start child bot processes; для containerized contour должен быть `false` | Панель |
| `SHARED_CONFIG_DIR` | абсолютный путь к `config/shared` для containerized/runtime deployment | Панель и бот |
| `IGUANA_POSTGRES_DB` | имя БД в dockerized contour | compose/infrastructure |
| `IGUANA_POSTGRES_USER` | пользователь PostgreSQL в dockerized contour | compose/infrastructure |
| `IGUANA_POSTGRES_PASSWORD` | пароль PostgreSQL в dockerized contour | compose/infrastructure |
| `APP_POSTGRES_BIND_HOST` | bind host publish-порта PostgreSQL в dockerized contour; production-like default должен оставаться loopback-only | compose/infrastructure |
| `IGUANA_RABBITMQ_USER` | пользователь RabbitMQ в dockerized contour | compose/infrastructure |
| `IGUANA_RABBITMQ_PASSWORD` | пароль RabbitMQ в dockerized contour | compose/infrastructure |
| `APP_RABBITMQ_AMQP_BIND_HOST` | bind host AMQP publish-порта RabbitMQ | compose/infrastructure |
| `APP_RABBITMQ_HTTP_BIND_HOST` | bind host management UI publish-порта RabbitMQ | compose/infrastructure |
| `APP_REDIS_PORT` | publish-порт Redis в dockerized contour | compose/infrastructure |
| `APP_REDIS_BIND_HOST` | bind host publish-порта Redis | compose/infrastructure |
| `IGUANA_REDIS_PASSWORD` | пароль Redis в dockerized contour | Панель, бот, compose/infrastructure |
| `APP_STORAGE_OBJECT_BUCKET` | bucket object storage | Панель, бот, compose/infrastructure |
| `APP_STORAGE_OBJECT_REGION` | region object storage | Панель, бот, compose/infrastructure |
| `APP_STORAGE_OBJECT_ENDPOINT` | endpoint S3/MinIO | Панель и бот |
| `APP_STORAGE_OBJECT_BIND_HOST` | bind host publish-порта S3 API | compose/infrastructure |
| `APP_STORAGE_OBJECT_CONSOLE_BIND_HOST` | bind host publish-порта MinIO console | compose/infrastructure |
| `APP_STORAGE_OBJECT_ACCESS_KEY` | access key S3/MinIO | Панель, бот, compose/infrastructure |
| `APP_STORAGE_OBJECT_SECRET_KEY` | secret key S3/MinIO | Панель, бот, compose/infrastructure |
| `APP_PANEL_BIND_HOST` | bind host publish-порта `spring-panel`; production-like default должен оставаться loopback-only при использовании edge layer | compose/infrastructure |
| `VK_BOT_BIND_HOST` | bind host publish-порта VK webhook runtime | compose/infrastructure |
| `MAX_BOT_BIND_HOST` | bind host publish-порта MAX webhook runtime | compose/infrastructure |
| `IGUANA_PUBLIC_HOST` | публичный hostname для `nginx` edge contour | compose/infrastructure |
| `IGUANA_EDGE_HTTP_BIND_HOST` | bind host publish-порта `80` для `nginx` edge contour | compose/infrastructure |
| `IGUANA_EDGE_HTTP_PORT` | publish-порт HTTP ingress для `nginx` edge contour | compose/infrastructure |
| `IGUANA_EDGE_HTTPS_BIND_HOST` | bind host publish-порта `443` для `nginx` edge contour | compose/infrastructure |
| `IGUANA_EDGE_HTTPS_PORT` | publish-порт HTTPS ingress для `nginx` edge contour | compose/infrastructure |
| `IGUANA_EDGE_TLS_ENABLED` | включает TLS-ready конфигурацию `nginx`; требует `fullchain.pem` и `privkey.pem` в каталоге сертификатов | compose/infrastructure |
| `IGUANA_EDGE_CERTS_DIR` | host-side каталог с `fullchain.pem` и `privkey.pem` для TLS edge contour | compose/infrastructure |
| `IGUANA_SHARED_CONFIG_DIR` | bind-mount override для `config/shared` в docker-compose contour | compose/infrastructure |
| `IGUANA_ATTACHMENTS_DIR` | bind-mount override для `attachments` в docker-compose contour | compose/infrastructure |
| `IGUANA_LOGS_DIR` | bind-mount override для `logs` в docker-compose contour | compose/infrastructure |
| `IGUANA_BOT_DATABASES_DIR` | bind-mount override для `bot_databases` в docker-compose contour | compose/infrastructure |

## Базы данных

| Переменная | Описание | По умолчанию |
| --- | --- | --- |
| `APP_DB_PANEL_RUNTIME` | каноническая operational-база панели | `panel_runtime.db` |
| `APP_DB_PANEL_IDENTITY` | база пользователей панели | `panel_identity.db` |
| `APP_DB_BOT_RUNTIME` | shared bot runtime база | `bot_runtime.db` |
| `APP_DB_TICKETS` | legacy alias для `APP_DB_PANEL_RUNTIME` | `panel_runtime.db` |
| `APP_DB_USERS` | legacy alias для `APP_DB_PANEL_IDENTITY` | `panel_identity.db` |
| `APP_DB_BOT` | legacy alias для `APP_DB_BOT_RUNTIME` | `bot_runtime.db` |
| `SUPPORT_BOT_DATABASE_PATH` | явный shared SQLite bridge для `java-bot` compatibility mode | unset |
| `APP_DB_CLIENTS` | база клиентов | `clients.db` |
| `APP_DB_KNOWLEDGE` | база знаний | `knowledge_base.db` |
| `APP_DB_OBJECTS` | база объектов | `objects.db` |
| `APP_BOT_DATABASE_DIR` | каталог legacy per-channel shard-файлов `bot-<channelId>.db` для import/диагностики | `../bot_databases` |

## Хранилища

| Переменная | Описание | По умолчанию |
| --- | --- | --- |
| `APP_STORAGE_ATTACHMENTS` | вложения | `../attachments` |
| `APP_STORAGE_KNOWLEDGE_BASE` | файлы базы знаний | `../attachments/knowledge_base` |
| `APP_STORAGE_AVATARS` | аватары | `../attachments/avatars` |
| `APP_STORAGE_WEBFORMS` | формы | `../attachments/forms` |
| `APP_ADMIN_PYTHON_EXECUTABLE` | python executable для admin storage inventory | `python` |
| `APP_ADMIN_REPOSITORY_ROOT` | явный repo root для admin storage inventory | auto-detect |
| `APP_ADMIN_STORAGE_INVENTORY_TIMEOUT` | timeout запуска inventory из админки | `90s` |
| `APP_SECURITY_REMEMBER_ME_KEY` | секретный ключ remember-me cookie; во внешнем production-like контуре должен быть явно переопределён | `iguana-panel-remember-me` |
| `APP_SECURITY_BOOTSTRAP_ADMIN_USERNAME` | bootstrap username для первого administrator-пользователя, если в users/authorities ещё нет `ROLE_ADMIN` | unset |
| `APP_SECURITY_BOOTSTRAP_ADMIN_PASSWORD` | bootstrap password для первого administrator-пользователя, если в users/authorities ещё нет `ROLE_ADMIN` | unset |
| `APP_SECURITY_BOOTSTRAP_ADMIN_ALLOW_DEFAULT_CREDENTIALS_IN_SQLITE` | разрешить dev-friendly fallback `admin/admin` только для SQLite compatibility mode | `true` |

## Пример запуска

```bash
export TELEGRAM_BOT_TOKEN="123:ABC"
export APP_DB_MODE="postgresql"
export SPRING_DATASOURCE_URL="jdbc:postgresql://db.example.local:5432/iguana"
export SPRING_DATASOURCE_USERNAME="iguana"
export SPRING_DATASOURCE_PASSWORD="secret"
```

Для external PostgreSQL-режима рекомендуется явно фиксировать режим и стандартные Spring datasource-поля:

```bash
export APP_DB_MODE="postgresql"
export SPRING_DATASOURCE_URL="jdbc:postgresql://db.example.local:5432/iguana"
export SPRING_DATASOURCE_USERNAME="iguana"
export SPRING_DATASOURCE_PASSWORD="secret"
```

В этом режиме `spring-panel` использует единый primary datasource для runtime/business контуров, а `java-bot` получает тот же JDBC-контракт через переменные окружения и не должен инициализировать схему самостоятельно.

Для `java-bot` действует явная граница:

- в `APP_DB_MODE=sqlite` runtime сам поднимает local schema через `SqliteSchemaInitializer`, но panel-side child JDBC contract больше не должен запускать этот путь автоматически;
- в `APP_DB_MODE=postgresql` runtime получает готовый PostgreSQL datasource-контракт и не несёт `SPRING_SQL_INIT_MODE`/`schema-sqlite.sql` в production-path.
- в `APP_INTEGRATION_TRANSPORT_MODE=rabbitmq` bot-side business операции по ticket/channel/feedback/blacklist должны идти через `APP_PANEL_INTERNAL_API_*`; silent fallback в local `JPA/SQLite` business storage больше не считается допустимым live-path.
- для multi-instance bot ingress в production contour нужно использовать `APP_COORDINATION_MODE=redis`, чтобы `Telegram`/`VK`/`MAX` long-poll owner semantics не оставались process-local.
- для `VK`/`MAX` webhook multi-instance contour вместе с этим нужен shared bot session-state слой; он настраивается тем же coordination namespace и TTL через `APP_COORDINATION_BOT_SESSION_TTL`.

Для `spring-panel` действует ещё одно правило:

- локальные `APP_DB_*` SQLite-пути автоматически подставляются только в явном `APP_DB_MODE=sqlite`;
- normal runtime path с `APP_DB_MODE=postgresql` больше не получает скрытый SQLite compatibility bootstrap через `EnvDefaultsInitializer`.
- `APP_DB_SETTINGS` больше не входит в active runtime/env contract: отдельный `settings.db` registry layer удалён как legacy topology.
- во внешнем production-like контуре `spring-panel` теперь fail-fast останавливается, если `APP_INTERNAL_BOT_API_TOKEN` или `APP_SECURITY_REMEMBER_ME_KEY` оставлены на встроенных дефолтах.
- если во внешнем контуре в `users/user_authorities` ещё нет пользователя с `ROLE_ADMIN`, для первого старта нужно заранее передать `APP_SECURITY_BOOTSTRAP_ADMIN_USERNAME` и `APP_SECURITY_BOOTSTRAP_ADMIN_PASSWORD`.

Для first-run bootstrap после стартового production-slice `01-183` действует ещё одно правило:

- default bootstrap-path должен завершаться в `PostgreSQL + RabbitMQ`;
- SQLite first-run bootstrap больше не поддерживается ни как compatibility override, ни как fallback.
- bootstrap-скрипты `scripts/bootstrap-first-run.ps1` и `scripts/bootstrap-first-run.sh` для локального PostgreSQL-контура теперь сразу генерируют `APP_INTERNAL_BOT_API_TOKEN` и `APP_SECURITY_REMEMBER_ME_KEY`;
- bootstrap-скрипты `scripts/bootstrap-first-run.ps1` и `scripts/bootstrap-first-run.sh` на fresh install теперь также генерируют `IGUANA_POSTGRES_PASSWORD`, `IGUANA_RABBITMQ_PASSWORD`, `IGUANA_REDIS_PASSWORD`, `APP_STORAGE_OBJECT_ACCESS_KEY`, `APP_STORAGE_OBJECT_SECRET_KEY`, `MONITORING_CREDENTIALS_MASTER_KEY` и `IGUANA_GRAFANA_ADMIN_PASSWORD`;
- если в `config/shared/monitoring-credentials.key` уже лежит legacy AES key, bootstrap использует `MONITORING_CREDENTIALS_MASTER_KEY=base64:<legacy-key>` вместо ротации, чтобы старые `enc:v1:` monitoring credentials продолжили расшифровываться;
- `spring-panel/run-windows.bat` и `spring-panel/run-linux.sh` дополнительно умеют долечить старый локальный bootstrap-`.env`, если он был создан до этого изменения, но только для app-side секретов без persisted volume state; явные пользовательские env overrides по-прежнему имеют приоритет;
- bootstrap намеренно не ротирует автоматически существующие PostgreSQL/RabbitMQ/Redis/MinIO/Grafana credentials поверх уже инициализированных volumes: для этого нужен отдельный migration workflow.

Для containerized contour из [docs/docker-production-contour.md](docker-production-contour.md) действует ещё один практический инвариант:

- `APP_COORDINATION_MODE=redis`;
- `APP_STORAGE_OBJECT_MODE=s3`;
- `APP_BOT_AUTO_START_ENABLED=false`;
- `SHARED_CONFIG_DIR` и `APP_STORAGE_ATTACHMENTS` лучше задавать абсолютными container paths, а не рассчитывать на относительный working directory;
- publish bind hosts для внутренних infra/service портов должны по умолчанию оставаться loopback-only, а внешний ingress лучше собирать отдельным `nginx` edge layer через [docker-compose.production-edge.yml](../docker-compose.production-edge.yml).

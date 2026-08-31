# Persisted credential migration status runbook

Этот runbook закрывает первый безопасный slice задачи `01-222`: перед любой ротацией
или правкой `.env` на уже живом `dockerized production contour` нужно сначала
классифицировать текущее состояние persisted credentials без мутаций.

## 1. Зачем нужен helper

После `01-216` secure bootstrap умеет генерировать хорошие fresh-install secrets.
Но для уже существующих volumes простая замена значений в `.env` опасна:

- PostgreSQL и RabbitMQ хранят credential state в persisted data;
- Redis защищает persisted dataset runtime-паролем, который надо менять
  координированно для всех клиентов;
- MinIO держит объектные данные в volume и должен оставаться согласованным с
  runtime-контрактом доступа;
- Grafana хранит admin-state в persisted DB и может не принять новый пароль только
  потому, что он лежит в `.env`.

Helper не меняет `.env`, не ротирует секреты и не трогает volumes. Он только
показывает, какой компонент:

- `fresh` — volume ещё не инициализирован;
- `ready` — существующий state живой и текущий configured credential реально
  подтверждён;
- `migration_required` — уже есть persisted state, но credential drift/placeholder
  или live verification не дают безопасно считать контур готовым.

## 2. Как запускать

### Windows / PowerShell

```powershell
.\scripts\docker-production-credential-migration-status.ps1
.\scripts\docker-production-credential-migration-status.ps1 -Json
.\scripts\docker-production-credential-migration-status.ps1 -ProjectName tg_ref_b24_sup
```

### Bash / Linux

```bash
./scripts/docker-production-credential-migration-status.sh
./scripts/docker-production-credential-migration-status.sh --json
./scripts/docker-production-credential-migration-status.sh --project-name tg_ref_b24_sup
```

По умолчанию helper использует:

1. `COMPOSE_PROJECT_NAME` из process env или `.env`, если он задан;
2. иначе имя каталога репозитория.

## 3. Что именно проверяется

### PostgreSQL

- наличие compose-managed volume `iguana-postgres-data`;
- наличие non-default `IGUANA_POSTGRES_PASSWORD`;
- live auth в running container через `psql SELECT 1`.

### RabbitMQ

- наличие volume `iguana-rabbitmq-data`;
- наличие non-default `IGUANA_RABBITMQ_PASSWORD`;
- live auth через `rabbitmqctl authenticate_user`.

### Redis

- наличие volume `iguana-redis-data`;
- наличие non-default `IGUANA_REDIS_PASSWORD`;
- live auth через `redis-cli -a ... ping`.

### MinIO / S3

- наличие volume `iguana-minio-data`;
- наличие non-default `APP_STORAGE_OBJECT_ACCESS_KEY` и
  `APP_STORAGE_OBJECT_SECRET_KEY`;
- сверка configured credentials с live runtime env контейнера `minio`.

Важно: MinIO helper пока не делает полноценный S3 API probe с подписанным
доступом. На этом этапе он только выявляет drift между текущим runtime и
ожидаемым `.env` контрактом.

### Grafana

- наличие volume `iguana-grafana-data`;
- наличие non-default `IGUANA_GRAFANA_ADMIN_PASSWORD`;
- live auth по `http://<bind-host>:<port>/api/user`.

## 4. Как интерпретировать статусы

### `fresh`

Это clean install path. Компонент ещё не имеет persisted state и может быть
инициализирован bootstrap-generated секретом.

### `ready`

Компонент уже существует, но текущий configured credential реально подтверждён на
живом состоянии. Это не означает, что rotation уже выполнен. Это значит, что
drift между `.env` и persisted/live state сейчас не обнаружен.

### `migration_required`

Этот статус означает минимум одно из следующих состояний:

- secret пустой;
- secret всё ещё на documented default;
- volume есть, но live container не запущен, поэтому верификация невозможна;
- configured secret не проходит live auth;
- runtime env контейнера не совпадает с configured contract.

При `migration_required` нельзя просто переписать `.env` и перезапустить contour.

## 5. Безопасная следующая реакция

Если helper показал `migration_required`:

1. сначала зафиксировать свежий backup / restore evidence;
2. не менять секреты вслепую поверх existing volumes;
3. подготовить component-specific apply runbook;
4. только после успешной live verification обновлять `.env` и restart sequence.

## 6. Что helper пока не делает

- не выполняет apply/rotation;
- не меняет persisted passwords/users;
- не перезапускает сервисы;
- не делает signed S3 auth probe для MinIO;
- не закрывает rollback path автоматически.

Именно этот remaining contour вынесен в follow-up задачу `01-223`.

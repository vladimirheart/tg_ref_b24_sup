# Persisted credential rotation apply runbook

Этот runbook продолжает `01-223`, `01-224` и текущий Bash parity slice из `01-225`
после диагностического helper'а из `docs/runbooks/persisted-credential-migration-status.md`.

На текущем шаге controlled apply workflow покрывает:

- `postgresql`;
- `rabbitmq`;
- `redis`;
- `minio`.

Поддерживаются два entrypoint:

- PowerShell: `scripts/docker-production-credential-migration-apply.ps1`;
- Bash: `scripts/docker-production-credential-migration-apply.sh`.

По умолчанию workflow не делает изменений. Реальные действия происходят только с
явным `-Apply` в PowerShell или `--apply` в Bash.

## 1. Когда использовать

Используйте apply workflow, когда:

- `docker-production-credential-migration-status.ps1` или `.sh` показывает `migration_required`;
- нужный контейнер уже запущен и volume-backed contour существует;
- нужно убрать drift между live persisted credential и repository `.env`;
- важна coordinated restart choreography, а не простая перезапись `.env`.

## 2. Что делает script

Оба script entrypoint делают одно и то же:

1. находят живой контейнер нужного компонента;
2. определяют текущий рабочий credential-кандидат:
   - canonical `IGUANA_*`;
   - compatibility `SPRING_*`, если это локально релевантно;
   - documented fallback только если live auth реально проходит;
3. создают checkpoint `.env.credential-migration-<component>-<timestamp>.bak`;
4. выполняют controlled switch:
   - `postgresql` и `rabbitmq` меняют live credential до записи в `.env`;
   - `redis` сначала меняет live `requirepass`, затем пересоздаёт runtime;
   - `minio` меняет `.env`, пересоздаёт `minio` и `minio-init`, а потом проверяет bucket access;
5. пересоздают только нужные сервисы текущего compose project;
6. если observability overlay уже поднят, автоматически добавляют
   `docker-compose.production-observability.yml`, чтобы не потерять overlay-конфиг;
7. делают post-change verification;
8. при сбое выполняют best-effort rollback live credential и/или `.env`.

## 3. Dry-run

### PowerShell

```powershell
.\scripts\docker-production-credential-migration-apply.ps1 -Component postgresql
.\scripts\docker-production-credential-migration-apply.ps1 -Component rabbitmq
.\scripts\docker-production-credential-migration-apply.ps1 -Component redis
.\scripts\docker-production-credential-migration-apply.ps1 -Component minio
```

### Bash

```bash
./scripts/docker-production-credential-migration-apply.sh --component postgresql
./scripts/docker-production-credential-migration-apply.sh --component rabbitmq
./scripts/docker-production-credential-migration-apply.sh --component redis
./scripts/docker-production-credential-migration-apply.sh --component minio
```

На Windows используйте Git Bash, например:

```powershell
& 'C:\Program Files\Git\bin\bash.exe' -lc 'cd /c/Users/<user>/git_h/tg_ref_b24_sup && ./scripts/docker-production-credential-migration-apply.sh --component redis'
```

Dry-run не раскрывает новый secret и не меняет ни `.env`, ни live runtime.

## 4. Real apply

### PowerShell

```powershell
.\scripts\docker-production-credential-migration-apply.ps1 -Component redis -Apply
.\scripts\docker-production-credential-migration-apply.ps1 -Component minio -Apply
```

### Bash

```bash
./scripts/docker-production-credential-migration-apply.sh --component redis --apply
./scripts/docker-production-credential-migration-apply.sh --component minio --apply
```

Можно передавать свои целевые секреты:

```powershell
.\scripts\docker-production-credential-migration-apply.ps1 -Component redis -Apply -TargetPassword "<secret>"
```

```bash
./scripts/docker-production-credential-migration-apply.sh --component redis --apply --target-password "<secret>"
```

```powershell
.\scripts\docker-production-credential-migration-apply.ps1 -Component minio -Apply -TargetAccessKey "<access-key>" -TargetSecretKey "<secret-key>"
```

```bash
./scripts/docker-production-credential-migration-apply.sh --component minio --apply --target-access-key "<access-key>" --target-secret-key "<secret-key>"
```

## 5. Какие env-ключи обновляются

### PostgreSQL

- всегда `IGUANA_POSTGRES_PASSWORD`;
- дополнительно `SPRING_DATASOURCE_PASSWORD`, если локальный datasource в `.env`
  указывает на `localhost` / `127.0.0.1` или ключ ещё пустой.

### RabbitMQ

- всегда `IGUANA_RABBITMQ_PASSWORD`;
- дополнительно `SPRING_RABBITMQ_PASSWORD`, если локальный host в `.env`
  указывает на `localhost` / `127.0.0.1` или ключ ещё пустой.

### Redis

- всегда `IGUANA_REDIS_PASSWORD`;
- дополнительно `SPRING_DATA_REDIS_PASSWORD`, если локальный host в `.env`
  указывает на `localhost` / `127.0.0.1` или ключ ещё пустой.

### MinIO

- всегда `APP_STORAGE_OBJECT_ACCESS_KEY`;
- всегда `APP_STORAGE_OBJECT_SECRET_KEY`.

## 6. Какие сервисы пересоздаются

### После PostgreSQL rotation

- `ops-worker`;
- `panel-web`;
- `postgres-exporter`, если observability overlay уже поднят.

### После RabbitMQ rotation

- `ops-worker`;
- `panel-web`;
- `bot-telegram`, `bot-vk`, `bot-max`, если они реально запущены.

### После Redis rotation

- `redis`;
- `redis-exporter`, если observability overlay уже поднят;
- `ops-worker`;
- `panel-web`;
- `bot-telegram`, `bot-vk`, `bot-max`, если они реально запущены.

### После MinIO rotation

- `minio`;
- `minio-init`;
- `ops-worker`;
- `panel-web`;
- `bot-telegram`, `bot-vk`, `bot-max`, если они реально запущены.

## 7. Как проходит verification

### PostgreSQL

- live `psql SELECT 1`;
- auth check после recreate.

### RabbitMQ

- `rabbitmqctl authenticate_user`;
- повторная auth-проверка после recreate.

### Redis

- `redis-cli --no-auth-warning -a <password> ping`;
- повторная auth-проверка после coordinated recreate.

### MinIO

- проверка текущего runtime env `MINIO_ROOT_USER` / `MINIO_ROOT_PASSWORD`;
- bucket access probe через ephemeral `minio/mc` в compose network;
- повторная bucket/access проверка после recreate и `minio-init`.

В Git Bash MinIO probe уже защищён от MSYS path conversion через
`MSYS_NO_PATHCONV=1` и `MSYS2_ARG_CONV_EXCL='*'`, чтобы `--entrypoint /bin/sh`
не превращался в Windows-путь.

## 8. Что осталось вне этого apply-path

- `grafana` пока только диагностируется, но не ротируется через controlled apply-path;
- нет bulk rotation нескольких компонентов за один прогон;
- нет автоматического backup snapshot перед apply, только `.env` rollback checkpoint;
- нужен rehearsal flow для multi-component rotation.

Этот remaining contour вынесен в `01-226`.

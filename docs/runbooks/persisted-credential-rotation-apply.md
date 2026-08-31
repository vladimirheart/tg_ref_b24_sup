# Persisted credential rotation apply runbook

Этот runbook продолжает `01-223`, `01-224`, `01-225` и текущий Grafana slice из
`01-226` после диагностического helper'а из
`docs/runbooks/persisted-credential-migration-status.md`.

На текущем шаге controlled apply workflow покрывает:

- `postgresql`;
- `rabbitmq`;
- `redis`;
- `minio`;
- `grafana`.

Поддерживаются два entrypoint:

- PowerShell: `scripts/docker-production-credential-migration-apply.ps1`;
- Bash: `scripts/docker-production-credential-migration-apply.sh`.

По умолчанию workflow не делает изменений. Реальные действия происходят только с
явным `-Apply` в PowerShell или `--apply` в Bash.

Для orchestration-слоя теперь поддерживаются:

- PowerShell: `-Components`, `-Component all`, `-Rehearsal`, `-BackupDirectory`;
- Bash: `--components`, `--component all`, `--rehearsal`, `--backup-dir`.

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
   - `grafana` делает `grafana cli admin reset-admin-password`, потом обновляет `.env` и пересоздаёт сам сервис;
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
.\scripts\docker-production-credential-migration-apply.ps1 -Component grafana
```

### Bash

```bash
./scripts/docker-production-credential-migration-apply.sh --component postgresql
./scripts/docker-production-credential-migration-apply.sh --component rabbitmq
./scripts/docker-production-credential-migration-apply.sh --component redis
./scripts/docker-production-credential-migration-apply.sh --component minio
./scripts/docker-production-credential-migration-apply.sh --component grafana
```

На Windows используйте Git Bash, например:

```powershell
& 'C:\Program Files\Git\bin\bash.exe' -lc 'cd /c/Users/<user>/git_h/tg_ref_b24_sup && ./scripts/docker-production-credential-migration-apply.sh --component grafana'
```

Dry-run не раскрывает новый secret и не меняет ни `.env`, ни live runtime.

### Multi-component rehearsal

PowerShell:

```powershell
.\scripts\docker-production-credential-migration-apply.ps1 -Components redis,grafana -Rehearsal
.\scripts\docker-production-credential-migration-apply.ps1 -Component all -Rehearsal
```

Bash:

```bash
./scripts/docker-production-credential-migration-apply.sh --components redis,grafana --rehearsal
./scripts/docker-production-credential-migration-apply.sh --component all --rehearsal
```

В этом режиме orchestration-слой:

1. нормализует список компонентов в канонический порядок cutover;
2. вызывает per-component dry-run по шагам;
3. останавливается на первом сбое preflight;
4. печатает явные шаги `Starting step N/M` и `Completed step N/M`.

## 4. Real apply

### PowerShell

```powershell
.\scripts\docker-production-credential-migration-apply.ps1 -Component grafana -Apply
```

### Bash

```bash
./scripts/docker-production-credential-migration-apply.sh --component grafana --apply
```

Можно передавать свои целевые секреты:

```powershell
.\scripts\docker-production-credential-migration-apply.ps1 -Component grafana -Apply -TargetPassword "<secret>"
```

```bash
./scripts/docker-production-credential-migration-apply.sh --component grafana --apply --target-password "<secret>"
```

Аналогично остаются supported apply-path для `postgresql`, `rabbitmq`, `redis` и `minio`.

### Bulk apply с snapshot hook

PowerShell:

```powershell
.\scripts\docker-production-credential-migration-apply.ps1 -Component all -Apply -BackupDirectory .\artifacts\credential-rotation
```

Bash:

```bash
./scripts/docker-production-credential-migration-apply.sh --component all --apply --backup-dir ./artifacts/credential-rotation
```

`BackupDirectory` / `--backup-dir` не подменяет штатные backup-задачи БД и object storage, но даёт
оператору быстрый pre-apply snapshot orchestration-сессии:

- копию `.env` как `env.before`;
- порядок шага rotation в `component-order.txt`;
- текущий список compose-контейнеров в `docker-ps.txt`.

Если snapshot hook указан без real apply, orchestration-слой выводит предупреждение и ничего не создаёт.

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

### Grafana

- всегда `IGUANA_GRAFANA_ADMIN_PASSWORD`.

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

### После Grafana rotation

- `grafana`.

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

### Grafana

- live auth по `http://<bind-host>:<port>/api/user`;
- `grafana cli --homepath /usr/share/grafana --config /etc/grafana/grafana.ini admin reset-admin-password`;
- повторная auth-проверка после recreate.

В Git Bash MinIO probe уже защищён от MSYS path conversion через
`MSYS_NO_PATHCONV=1` и `MSYS2_ARG_CONV_EXCL='*'`, чтобы `--entrypoint /bin/sh`
не превращался в Windows-путь.

## 8. Bulk order и rollback expectations

Для multi-component запуска orchestration всегда нормализует шаги в один и тот же порядок:

1. `postgresql`
2. `rabbitmq`
3. `redis`
4. `minio`
5. `grafana`

Почему именно так:

- сначала ротируются базовые data-plane зависимости;
- затем transport/runtime coordination;
- затем object storage;
- observability admin credential остаётся последним, чтобы не мешать доступу к диагностике во время cutover.

Rollback expectations остаются component-scoped:

- каждый per-component apply по-прежнему держит свой `.env.credential-migration-<component>-<timestamp>.bak`;
- live rollback выполняется best-effort внутри конкретной ветки компонента;
- orchestration-слой не делает глобальный cross-component rollback всей цепочки, а останавливается на первом failed step;
- pre-apply snapshot нужен как операторская опора для ручного анализа и recovery, а не как замена restore-процедурам.

## 9. Что осталось вне этого apply-path

- off-host backup/restore evidence для PostgreSQL и MinIO остаётся в задачах `01-212` и `01-220`;
- если понадобится регулярный scheduled rehearsal, его лучше выносить в отдельный automation/CI слой, а не в inline shell workflow;
- real production cutover всё ещё требует операторского окна, проверки логов и пост-фактум evidence.

Bulk rotation, rehearsal flow и snapshot hook, ради которых был создан `01-227`, теперь закрыты в этом runbook.

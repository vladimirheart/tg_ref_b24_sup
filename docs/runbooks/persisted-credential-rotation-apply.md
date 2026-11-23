# Persisted credential rotation apply runbook

## 0. Как устроена конфигурация и что проверить перед ротацией

Актуализация: **2026-11-23**, задача `01-236`. Ротация паролей и установка
исправленных серверных версий — отдельные необходимые меры. Статус `ready`
credential-helper не доказывает отсутствие уязвимостей в образе.

Настройки Docker-инфраструктуры задаются в корневом `.env` (локальный файл,
исключённый из Git) или в окружении процесса запуска. `.env.example` — шаблон
с документированными локальными defaults, а не хранилище production-секретов.
`docker-compose.production-contour.yml` передаёт значения серверам и ролям
`panel-web`, `ops-worker`, `bot-runner`. Observability-клиенты подключаются через
`docker-compose.production-observability.yml`, backup-клиенты — через
`docker-compose.production-backup.yml`. Админка панели не заменяет этот механизм.

| Компонент и назначение | Канонические настройки | Где действует пароль и как его менять |
| --- | --- | --- |
| PostgreSQL: бизнес-данные | `IGUANA_POSTGRES_DB`, `IGUANA_POSTGRES_USER`, `IGUANA_POSTGRES_PASSWORD` | Пароль роли хранится в БД volume `iguana-postgres-data`; сначала `ALTER USER`, затем согласование клиентов. `POSTGRES_PASSWORD` инициализирует пароль только на пустом data directory. |
| RabbitMQ: доставка событий | `IGUANA_RABBITMQ_USER`, `IGUANA_RABBITMQ_PASSWORD` | Учётная запись хранится в `iguana-rabbitmq-data`; сначала `rabbitmqctl change_password`, затем клиенты. Изменение `RABBITMQ_DEFAULT_PASS` не ротирует существующего пользователя. |
| Redis: координация, leases, сессии | `IGUANA_REDIS_PASSWORD` | В этом Compose `requirepass` задаётся аргументом запуска; volume `iguana-redis-data` содержит AOF/dataset. `CONFIG SET requirepass` меняет текущий процесс; новый пароль закрепляется в `.env` и через recreate Redis и клиентов. |
| MinIO: вложения и другие S3-объекты | `APP_STORAGE_OBJECT_ACCESS_KEY`, `APP_STORAGE_OBJECT_SECRET_KEY` | Compose передаёт их как `MINIO_ROOT_USER` / `MINIO_ROOT_PASSWORD`. Root credential меняется через environment и recreate; объекты остаются в `iguana-minio-data`. Это не SQL-подобная смена сохранённого пароля. |

Основания: [инициализация PostgreSQL](https://docs.docker.com/guides/postgresql/networking-and-connectivity/),
[учётные записи RabbitMQ](https://www.rabbitmq.com/docs/access-control),
[Redis CONFIG SET](https://redis.io/docs/latest/commands/config-set/),
[root credentials MinIO OSS](https://github.com/minio/minio/blob/master/docs/config/README.md).

Приоритет: непустое process environment → `.env` → Compose default.
Перед apply нужно устранить устаревшие overrides в запускающем процессе:
скрипт пишет новые значения в `.env`, а старое окружение может перекрыть их при
recreate. Проверять наличие ключей следует без вывода значений.
Отдельно проверить `APP_BOTS_INFRASTRUCTURE_RABBITMQ_MANAGEMENT_PASSWORD`:
явно заданный override не обновляется вместе с `IGUANA_RABBITMQ_PASSWORD`.
Если это тот же пользователь брокера, оба источника должны быть согласованы.

### Проверка официальных advisories на 2026-11-23

Ниже — проверка исходной конфигурации, а не инвентаризация работающего сервера.
Плавающий tag не доказывает, какая версия уже находится в локальном image cache.

| Компонент в Compose | Подтверждённый риск | Действие до production |
| --- | --- | --- |
| `postgres:16-alpine` | В том числе CVE-2026-6460: выполнение кода через type confusion; исправление для ветки 16 опубликовано 2026-11-12 в 16.16. | Проверить фактический `postgres --version`; установить актуальный patch ветки 16, минимум 16.16 для этого advisory. Обновить также образы backup/restore с клиентскими утилитами. [Advisory](https://www.postgresql.org/support/security/CVE-2026-6460/), [список для ветки 16](https://www.postgresql.org/support/security/16/). |
| `rabbitmq:3.13-management-alpine` | Community support ветки 3.13 закончился 2024-09-30. Среди advisories 2026-10-25 — GHSA-mhh3-4wv2-rrj7, раскрытие SASL credentials в диагностике `amqp10_client`; применимость зависит от версии и используемого функционала. | Подготовить переход на поддерживаемую ветку; не заменять major tag поверх volume без rehearsal. Для rolling-перехода к 4.3/4.4 документация требует промежуточную 4.2, включённые stable feature flags и проверку Khepri. [Поддержка](https://www.rabbitmq.com/release-information), [advisories](https://www.rabbitmq.com/security), [upgrade](https://www.rabbitmq.com/docs/upgrade). |
| `redis:7-alpine` | CVE-2025-49844: RCE через Lua, исправлено в 7.4.6; более поздние security fixes включены в 7.4.11. CVE-2026-81934 затрагивает TLS pending-data path, поэтому для текущего plaintext Compose этот путь не доказан. | Проверить `redis-server --version`, установить актуальный patch выбранной ветки; 7.4.11 — минимум для перечисленных fixes ветки 7.4. Сохранить доступ только доверенным клиентам; ACL вводить после проверки используемых команд. [Lua advisory](https://redis.io/blog/security-advisory-cve-2025-49844/), [TLS advisory](https://redis.io/blog/security-advisory-cve-2026-81934/), [7.4 release notes](https://redis.io/docs/latest/operate/oss_and_stack/stack-with-enterprise/release-notes/redisce/redisce-7.4-release-notes/). |
| `minio/minio:RELEASE.2025-07-23T15-54-02Z` | CVE-2025-62506: обход session policy. Новее — GHSA-hv4r-mvr4-25vw и GHSA-9c4q-hq6p-c237: запись объектов без secret key при знании действующего access key. Закреплённый релиз входит в указанные affected ranges. | Смена пароля недостаточна. Репозиторий OSS архивирован 2026-04-25; апрельские advisories указывают исправление в **AIStor**, а не новый tag `minio/minio`. До production выбрать поддерживаемый S3-сервер/дистрибутив и доказать backup/restore, совместимость объектов и авторизацию. [Session policy](https://github.com/minio/minio/security/advisories/GHSA-jjjj-jwhf-8rgr), [unsigned trailer](https://github.com/minio/minio/security/advisories/GHSA-hv4r-mvr4-25vw), [Snowball](https://github.com/minio/minio/security/advisories/GHSA-9c4q-hq6p-c237), [статус OSS](https://github.com/minio/minio). |

Loopback binds в Compose уже ограничивают публикацию инфраструктурных портов
значением `127.0.0.1`. Сохранять их; не открывать PostgreSQL, RabbitMQ management,
Redis и MinIO console в интернет. Это ограничивает доступ с хоста, но не заменяет
защиту внутри Docker network и проверку реальных binds/firewall.

### Бесплатный путь замены MinIO

Пользователь выбрал бесплатное решение. **Первый кандидат для rehearsal — Garage**:
официальный список содержит release build `v2.5.0` от 2026-10-24; лицензия AGPLv3.
Это техническая рекомендация для проверки совместимости, не утверждение о
безопасности конкретного образа или подтверждённой работе с нашими данными.
[Релизы](https://garagehq.deuxfleurs.fr/_releases.html),
[лицензия](https://github.com/deuxfleurs-org/garage/blob/main-v2/LICENSE).

Выбор основан на коде: panel `AttachmentObjectStorageService` и bot
`AttachmentService` используют AWS SDK v2 с path-style endpoint и базовыми
`HeadBucket` / `HeadObject` / `PutObject` / `GetObject` / `DeleteObject`.
Garage декларирует эти операции; ACL/bucket policy заменены собственной системой
прав, object versioning не поддерживается. Поэтому `minio-init` с
`mc anonymous set none` и root-env rotation нельзя переносить без адаптации.
[S3 compatibility](https://garagehq.deuxfleurs.fr/documentation/reference-manual/s3-compatibility/).

План проверки: отдельные новые volumes и закреплённый release/digest; приватный
bucket и отдельный ключ приложения; RPC/admin не публиковать, admin/RPC/metrics
секреты хранить отдельно от S3-ключа. Проверить AWS SDK uploads, Content-Type,
SHA-256 каждого перенесённого объекта, чтение/удаление, restart, отказ неверных
credentials и доступ только к разрешённому bucket. Перенести данные через S3
API с сохранением object keys и metadata; формат MinIO volume не совместим с
новым сервером. Перед финальной синхронизацией остановить запись вложений.
До приёмки не удалять исходное хранилище и backup.
[Настройки Garage](https://garagehq.deuxfleurs.fr/documentation/reference-manual/configuration/).

Отдельно адаптировать endpoint/region в Compose (сейчас `http://minio:9000`
закреплён явно), bootstrap/status/rotation, backup/restore и Prometheus job
`/minio/v2/metrics/cluster`. `mc` поддерживается как S3-клиент, но это не означает
совместимость административных операций MinIO.
[Поддерживаемые клиенты](https://garagehq.deuxfleurs.fr/documentation/quick-start/).

SeaweedFS рассмотрен как альтернатива, но требует отдельной проверки актуальных
advisories и изоляции внутренних master/volume/filer/gRPC API. Его security policy
явно предполагает доверенную внутреннюю сеть; поэтому простая замена image tag
не является готовым планом безопасного развёртывания.
[Security policy и advisories](https://github.com/seaweedfs/seaweedfs/security).

### Безопасный порядок выполнения

1. На машине с нужным Docker daemon зафиксировать версии и digest образов,
   published ports и состояние backup/restore. В сессии аудита 2026-11-23
   `desktop-linux` не подключается к Engine; текущие live credentials не проверены.
   Исторический `migration_required` не является новой live-проверкой.
2. Выполнить status и rehearsal без apply. Rehearsal проверяет preflight/auth
   текущих credentials; он не доказывает будущий rollback и не заменяет
   изолированный restore/restart drill. Закрыть DR prerequisite `01-212` / `01-271`.
3. Отдельно подготовить обновления серверов и выбор S3-решения из таблицы выше.
   Проверить целевые версии на копиях данных, сохранить rollback point.
4. В согласованное окно ротировать по одному компоненту. Без target-аргументов
   helper генерирует отдельный случайный secret из 32 байт; для MinIO access key —
   из 12 байт. Не вводить секреты в командную строку, transcript или shell tracing.
5. После каждого шага проверить новые credentials и отказ старых в новом
   соединении, readiness всех клиентов, доставку сообщений и чтение вложений.
   MinIO требует signed S3 probe; совпадение environment само по себе недостаточно.
   Успешные `SELECT 1` / `PONG` дополнить отказом заведомо неверного пароля,
   чтобы исключить `trust` / отсутствие auth.
6. После recreate повторить проверки, включая `bot-runner`, migrator при следующем
   штатном запуске и backup/exporter-клиентов. Не удалять volumes для смены пароля.

Snapshot `env.before` и `.env.credential-migration-*.bak` содержат полный открытый
текст секретов. Хранить их в приватном каталоге с проверенными ACL/permissions,
предпочтительно вне checkout; Git ignore не обеспечивает шифрование или права ОС.
Не публиковать `docker compose config`, полный `docker inspect` или process args:
текущий env/CLI-контракт виден администратору Docker и хоста.

Защитные исправления этого аудита: PowerShell helper не печатает аргументы и raw
output при ошибке; Bash подавляет raw output команд смены пароля и останавливает
bulk flow при первом failed child; recreate использует `--no-deps`, чтобы не
запускать невыбранные зависимости и `db-migrate`; snapshot-каталоги исключены из Git.
Эти изменения не заменяют live rotation и обновление серверов.

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

Для обычной ротации оставляйте target-аргументы пустыми: скрипт сам генерирует
секрет и сохраняет его в локальном `.env`. Передача явного секрета через
`-TargetPassword` / `--target-password` поддерживается для совместимости, но
может раскрыть его в истории команд и process list.

Аналогично остаются supported apply-path для `postgresql`, `rabbitmq`, `redis` и `minio`.

### Bulk apply с snapshot hook

PowerShell:

```powershell
.\scripts\docker-production-credential-migration-apply.ps1 -Component all -Apply -BackupDirectory ..\iguana-runtime\credential-checkpoints
```

Bash:

```bash
./scripts/docker-production-credential-migration-apply.sh --component all --apply --backup-dir ../iguana-runtime/credential-checkpoints
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
- `postgres-exporter`, если observability overlay уже поднят;
- bot runtime target выбирается взаимоисключающе: running `bot-runner`, либо — только если supervisor отсутствует — реально запущенные emergency `bot-telegram` / `bot-vk` / `bot-max`.

### После RabbitMQ rotation

- `ops-worker`;
- `panel-web`;
- тот же взаимоисключающий bot runtime target.

### После Redis rotation

- `redis`;
- `redis-exporter`, если observability overlay уже поднят;
- `ops-worker`;
- `panel-web`;
- тот же взаимоисключающий bot runtime target.

### После MinIO rotation

- `minio`;
- `minio-init`;
- `ops-worker`;
- `panel-web`;
- тот же взаимоисключающий bot runtime target.

### Runtime ownership guard

Перед каждым component dry-run/rehearsal/apply workflow проверяет mixed bot runtime ownership. Если одновременно запущены `bot-runner` и хотя бы один legacy static service, rotation немедленно блокируется до любых credential changes. Нельзя пересоздавать оба ownership-контура в одной операции.

Static services `bot-telegram`, `bot-vk`, `bot-max` удалены из canonical production compose и находятся в `docker-compose.production-legacy-bots.yml` с `restart: "no"`. Запускать их можно только через `scripts/docker-production-legacy-bots.ps1/.sh`, где `bot-runner` обязан быть остановлен. Если credential rotation видит реально запущенный legacy runtime без supervisor, workflow автоматически добавляет emergency compose к recreate model; mixed ownership по-прежнему блокируется до любых credential changes.

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

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

## 10. Расширенная карта безопасности: что, где, когда и как менять

Актуализация 2026-11-23. Пользователь продолжает разбор безопасности, MinIO
разбирает самостоятельно. Ниже — карта по исходному коду и рекомендуемый порядок,
а не подтверждение изменения production. Значения секретов в реестр не включать.

### Места хранения

- `.env` / окружение запуска: серверные пароли, внутренние master/signing keys,
  remember-me key, общий AES key, bootstrap admin и настройки TLS/портов.
  Process environment перекрывает `.env`; при rotation проверить overrides.
- `IGUANA_SHARED_CONFIG_DIR`: действующие `settings.json`, `bot_credentials.json`
  и runtime-конфигурация. Default вне checkout:
  `../iguana-runtime/tg_ref_b24_sup/shared-config`. Git-каталог `config/shared`
  является seed/test fixture; его правка не меняет существующий runtime.
- PostgreSQL: пользователи панели и их hashes, channel credentials при прямом
  token binding, RMS monitoring credentials и отдельные child transport identities.
- `IGUANA_SECRETS_DIR` / `config/secrets`: файл
  `alertmanager-ingestion.token`, монтируемый в panel и Alertmanager.
- TLS: `IGUANA_EDGE_CERTS_DIR` с `fullchain.pem` и `privkey.pem`.

| Что | Где менять | Когда | Как и что проверить |
| --- | --- | --- | --- |
| PostgreSQL / RabbitMQ / Redis | Сервис и `.env`, разделы 3–8 этого runbook | Defaults — до production; немедленно при утечке; затем по принятому сроку служебных secrets | Штатный component apply после backup/rehearsal. Новое соединение с новым credential проходит, старый/неверный отклоняется; backend, children, exporters и backup работают. |
| Grafana admin | Grafana и `IGUANA_GRAFANA_ADMIN_PASSWORD` | Default — до production; утечка/передача общего доступа | Helper с `-Component grafana`; правка `.env` сама не заменяет пароль в существующей Grafana DB. Отдельно проверить пользователей, sessions и API/service-account tokens. |
| Пользователь/администратор панели | Собственный профиль либо управление пользователями | Слабый/default/известный другим пароль, подозрение на компрометацию | Штатная смена пишет bcrypt. Bootstrap `APP_SECURITY_BOOTSTRAP_ADMIN_PASSWORD` не меняет пароль существующей записи. После первоначального создания убрать bootstrap password из постоянного env, сохранив проверенный recovery-доступ. |
| Telegram / VK / MAX bot token | Сначала сервис-провайдер; затем credential catalog/тот же канал в панели | Утечка, смена владельца/прав, expiry или политика провайдера | Остановить нужный канал, перевыпустить token, сохранить новый credential и binding, запустить канал. Проверить входящее и исходящее сообщение, вложение и webhook при его использовании; старый token отозвать. Не удалять сам канал ради смены token. |
| iiko / Bitrix / NetBox / SMTP и прочие интеграции | Провайдер/сервис и соответствующие настройки панели/мониторинга | Expiry, утечка, смена сервисного аккаунта/доступов | По возможности выпустить второй ключ, переключить единственного клиента, проверить реальную операцию, отозвать старый. Для Bitrix секрет может быть частью webhook URL — защищать URL целиком. |
| Alertmanager Bearer token | `alertmanager-ingestion.token` и оба его потребителя | Утечка и принятый срок служебных secrets | Создать приватный checkpoint, заменить файл и пересоздать panel-web/alertmanager для надёжного обновления bind mount. Проверить аутентифицированную доставку тестового alert, отказ старого token и публичный deny маршрута. Не отключать auth ради smoke. |
| `APP_SECURITY_REMEMBER_ME_KEY` | `.env`, все web replicas | Default — до production; компрометация cookie/key; управляемый сброс remember-me | Новый случайный key и согласованный recreate web. Старые remember-me cookies должны перестать восстанавливать вход; уже открытые HTTP/JDBC sessions требуют отдельного отзыва. |
| `APP_INTERNAL_BOT_API_TOKEN` и `APP_INTERNAL_BOT_API_SIGNATURE_SECRET` | `.env`, backend/runner, все производные child API/SQL/Rabbit credentials | Default/утечка — обязательная смена, но существующие каналы требуют подготовленного cutover | Это корневые secrets: из них через HMAC выводятся child credentials. Простая замена env сломает существующие identities; auto-provision намеренно не выполняет такую rotation. Нужны drain/pause, согласованное обновление всех credentials/journal и проверка outbox/queues. Готовый безопасный массовый migration path текущим аудитом не подтверждён. |
| `MONITORING_CREDENTIALS_MASTER_KEY` | `.env` всех backend roles и зашифрованные RMS credentials в БД | Компрометация или отдельная политика cryptoperiod | Это AES data-encryption key, не пароль входа. Нужны old-key decrypt → new-key encrypt, проверка всех записей, согласованное переключение roles и restore drill. `enc:v1:` не содержит key ID/keyring; просто заменить env нельзя. Старый ключ нужен для старых backups до их безопасного истечения/перешифровки. |
| TLS private key и certificate | CA/ACME и `IGUANA_EDGE_CERTS_DIR` | Сертификат — до expiry; ключ — немедленно при утечке | Проверить пару certificate/key и цепочку, безопасно заменить, reload/recreate edge, проверить HTTPS снаружи. При утечке отозвать старый сертификат. |
| Host / SSH / VPN / Git / CI / backup credentials | ОС и соответствующие внешние системы | Утечка, увольнение/смена подрядчика, expiry | Именные аккаунты, минимальные scopes, MFA где доступна; отзыв keys/tokens и sessions проверяется у каждого сервиса. Фактический состав этих доступов данным source-аудитом не инвентаризирован. |

Заполнение прежде пустого `APP_INTERNAL_BOT_API_SIGNATURE_SECRET` тоже меняет
производные credentials существующих children: fallback сейчас использует token.
Такую операцию нельзя считать независимым добавлением новой настройки.

### Когда менять: рекомендуемая политика

- Сразу: документированные defaults и подтверждённая/подозреваемая утечка.
  Если скомпрометирован хост, сначала ограничить доступ и устранить причину;
  иначе новый secret может быть украден повторно. Проверить все доступные этому
  хосту secrets и активные sessions.
- Пользовательские пароли: уникальные, рекомендуемая длина от 15 символов для
  однофакторного входа; учитывать текущий предел bcrypt 72 байта UTF-8. Не вводить
  механическую смену каждые 90 дней. Это рекомендация, а не новая автоматически
  внедрённая политика форм. [NIST SP 800-63B](https://pages.nist.gov/800-63-4/sp800-63b.html).
- Служебные пароли/tokens: определить срок по доступам и возможности автоматизации.
  Как начальную **локальную политику** можно принять 90 дней для независимых
  static credentials после успешного rehearsal, затем сокращать при автоматизации.
  Это не универсальный норматив и не расписание для AES/master derivation keys.
  [OWASP Secret Lifecycle](https://cheatsheetseries.owasp.org/cheatsheets/Secrets_Management_Cheat_Sheet.html).
- При удалении сотрудника/подрядчика: отозвать его именные доступы сразу;
  менять общие secrets, к которым он имел доступ. Контрольный пересмотр прав —
  ежеквартально как предлагаемая политика проекта.
- Сертификаты: автоматическое продление и контроль срока. Образы/ОС/зависимости:
  регулярный patch review; приоритет и срок исправления CVE определять по
  доступности уязвимого пути и severity, без ожидания срока смены пароля.

### Что требует отдельной доработки по результатам source-аудита

1. Проверка пароля панели допускала raw equality до распознавания bcrypt.
   Это позволяло предъявить сам сохранённый hash как пароль. Исправленный source
   проверяет bcrypt только bcrypt-verifier; legacy plaintext fallback оставлен
   лишь для значений, не являющихся распознанным bcrypt hash. Регрессия включает
   реальный Spring Security form-login pipeline с mocked JDBC.
2. Смена пароля и remember-me key сами по себе не гарантируют прекращение всех
   уже открытых JDBC sessions. В текущем source добавлен явный per-user/fleet
   session revoke через session_version (раздел 12). После reset/компрометации
   вызвать его отдельно: смена пароля автоматически не увеличивает эту версию.
   SessionAuthorityFilter также отзывает blocked/disabled/renamed identities.
   [OWASP Sessions](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html).
3. LegacyCompatiblePasswordEncoder поддерживает старые plaintext passwords.
   Наличие таких записей в рабочей БД здесь не проверялось. Нужен read-only
   подсчёт по типу без выгрузки значений, перевод на bcrypt через штатную смену
   и затем отказ от plaintext-совместимости.
4. `bot_credentials.json` сериализуется с token; маска в UI не шифрует файл.
   JSON-конфигурация и backups должны считаться секретными. Штатный file backup
   создаёт tar.gz и SHA-256, что не является шифрованием; защита destination
   зависит от среды и здесь не подтверждена. Ключи восстановления хранить отдельно
   с ограниченным доступом и проверять их совместимость с backup generation.
5. В просмотренных SecurityConfig и nginx templates не обнаружены native MFA и
   ограничение частоты login requests. Проверить компенсирующий VPN/access gateway
   с MFA и добавить rate limiting; публичные provider webhooks должны остаться
   отдельно доступными. Проверить secure/HttpOnly/SameSite cookies на реальном HTTPS.
6. Разделить migration/admin и обычные runtime DB/broker права. В стандартном
   Compose backend использует `IGUANA_POSTGRES_USER` и management credentials
   брокера; смена пароля не уменьшает эти полномочия.

Основания по хранению, ротации и восстановлению:
[OWASP Secrets Management](https://cheatsheetseries.owasp.org/cheatsheets/Secrets_Management_Cheat_Sheet.html),
[OWASP Key Management](https://cheatsheetseries.owasp.org/cheatsheets/Key_Management_Cheat_Sheet.html).

## 11. Пошаговый инструктаж: PostgreSQL, RabbitMQ, Redis и Grafana

Инструкция проверена по source helpers и официальным документам 2026-11-23.
Команды ниже предназначены для оператора **на машине с нужным Docker daemon**
и с актуальным production checkout. Здесь эти команды apply не выполнялись.
MinIO исключён из выбранных компонентов. Пароли панели iguana и пароль Grafana —
разные учётные записи. Смена инфраструктурных паролей не заменяет выход из панели.

### 11.1. Почему недостаточно поменять `.env`

| Сервис | Где живёт действующий пароль | Что согласованно меняет helper |
| --- | --- | --- |
| PostgreSQL | Пароль роли в системном каталоге PostgreSQL; каталог данных в volume | Live роль, `IGUANA_POSTGRES_PASSWORD`, подходящий локальный `SPRING_DATASOURCE_PASSWORD`, запущенные клиенты и exporter |
| RabbitMQ | Пользователь internal auth backend брокера в persisted state | Live пользователь, `IGUANA_RABBITMQ_PASSWORD`, подходящий локальный `SPRING_RABBITMQ_PASSWORD`, запущенные клиенты |
| Redis | Текущий `requirepass` и команда старта Redis | Live `CONFIG SET requirepass`, `IGUANA_REDIS_PASSWORD`, подходящий `SPRING_DATA_REDIS_PASSWORD`, Redis/exporter/клиенты |
| Grafana | Учётная запись в БД Grafana в volume, независимо от admin env bootstrap | CLI reset пользователя, `IGUANA_GRAFANA_ADMIN_PASSWORD`, recreate Grafana и проверка нового входа |

Имена пользователей здесь не меняем. `POSTGRES_PASSWORD`,
`RABBITMQ_DEFAULT_PASS`, `GF_SECURITY_ADMIN_PASSWORD` — начальные настройки;
новое значение в environment само по себе не доказывает смену существующей записи.
Для Redis одной runtime-команды недостаточно: следующий старт должен получить
то же значение. Иначе после перезапуска появится рассинхронизация.

Основания: [PostgreSQL ALTER ROLE](https://www.postgresql.org/docs/16/sql-alterrole.html),
[RabbitMQ access control](https://www.rabbitmq.com/docs/access-control),
[Redis CONFIG SET](https://redis.io/docs/latest/commands/config-set/),
[Grafana CLI](https://grafana.com/docs/grafana/latest/administration/cli/).

### 11.2. Подготовка перед первой сменой

1. Запланировать короткое окно обслуживания: helper пересоздаёт работающие
   приложения, поэтому HTTP/SSE, bot connections и фоновые операции могут прерваться.
   Завершить чувствительные операции, проверить штатный backup и возможность
   восстановления. Снимок `.env` не содержит БД, очереди или вложения.
2. Открыть PowerShell в корне production checkout. Проверить daemon и контекст:

   ```powershell
   docker context show
   docker info --format '{{.Name}} / {{.ServerVersion}}'
   docker ps --format 'table {{.Names}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}'
   docker ps --format '{{.Label "com.docker.compose.project"}} / {{.Label "com.docker.compose.service"}}'
   ```

   Если Engine недоступен или список относится к другому серверу, не продолжать.
   Выбрать **проверенное** имя Compose project из последней команды:

   ```powershell
   $rotationProject = 'ИМЯ_ВАШЕГО_COMPOSE_PROJECT'
   ```

3. Проверить наличие действующего локального `.env`, не выводя его целиком:

   ```powershell
   Test-Path -LiteralPath .env
   git status --short
   ```

   При отсутствии `.env` восстановить действующую конфигурацию из приватного
   хранилища/backup. Не копировать `.env.example` поверх работающего контура:
   там примерные значения, а не фактические секреты. При наличии внешнего vault,
   CI/CD или централизованной конфигурации согласовать запись нового секрета и туда,
   иначе следующий deploy вернёт старый пароль. Не выводить полный compose config.

4. Убедиться, что процесс PowerShell не переопределяет нужные ключи окружением.
   Проверка печатает **только имена**, без значений:

   ```powershell
   $rotationKeys = @('IGUANA_POSTGRES_PASSWORD','SPRING_DATASOURCE_PASSWORD',
     'IGUANA_RABBITMQ_PASSWORD','SPRING_RABBITMQ_PASSWORD',
     'IGUANA_REDIS_PASSWORD','SPRING_DATA_REDIS_PASSWORD',
     'IGUANA_GRAFANA_ADMIN_PASSWORD')
   $rotationKeys | Where-Object { Test-Path -LiteralPath ('Env:' + $_) }
   ```

   При обнаружении переопределений выяснить их источник и убрать именно устаревшие
   значения из среды запуска/secret store. Не очищать всё окружение.
   В этой реализации сам helper читает `.env`, а Compose учитывает и environment;
   несовпадающие источники нельзя оставлять перед recreate.

5. Подготовить приватный каталог вне Git, например
   `C:\iguana-private\credential-checkpoints`, с доступом только владельцу сервера
   и выбранным администраторам. Проверить ACL через `Get-Acl` / свойства папки.
   Helper также создаёт `.env.credential-migration-*.bak` рядом с `.env`;
   это открытые секреты, для них нужны такие же права. Не включать PowerShell
   transcript, shell tracing или запись терминала со значениями секретов.

6. Получить status и rehearsal **без изменений**:

   ```powershell
   .\scripts\docker-production-credential-migration-status.ps1 -ProjectName $rotationProject -Json
   .\scripts\docker-production-credential-migration-apply.ps1 -ProjectName $rotationProject -Components postgresql,rabbitmq,redis,grafana -Rehearsal
   ```

   Проверить project, доступность каждого сервиса, успешную текущую аутентификацию,
   список planned recreate и отсутствие mixed bot runtime ownership. `ready`
   означает согласованность распознанных credentials; можно ротировать и `ready`
   пароль. `migration_required` не обязательно означает известную CVE.
   При ошибке остановиться на её причине. Если Grafana отсутствует, сначала
   отдельно определить, развёрнут ли observability overlay; ротация его не устанавливает.

### 11.3. PostgreSQL: сменить первым и проверить приложения

Сначала отдельный dry-run, затем один apply:

```powershell
.\scripts\docker-production-credential-migration-apply.ps1 -ProjectName $rotationProject -Component postgresql
.\scripts\docker-production-credential-migration-apply.ps1 -ProjectName $rotationProject -Component postgresql -Apply -BackupDirectory C:\iguana-private\credential-checkpoints
```

Без `-TargetPassword` создаётся отдельное случайное значение из 32 байт
(64 hex-символа). Оно записывается в локальный `.env`, не в Git и не в вывод.
Helper меняет пароль существующей роли, затем пересоздаёт работающие
`panel-web`, `ops-worker`, выбранный bot runtime и `postgres-exporter`, если он
работает. Сервер PostgreSQL не нужно удалять или переинициализировать.
Старое `POSTGRES_PASSWORD` в environment уже существующего контейнера не является
текущим паролем роли: при сохранённом volume значение bootstrap не переустанавливает роль.

После успешного сообщения:

1. Повторить status-команду из 11.2, проверить PostgreSQL и клиентов.
2. Открыть панель, войти, прочитать диалоги и выполнить безопасную тестовую запись.
3. Проверить обработку тестового сообщения ботом и работу фонового обработчика.
4. Убедиться в успехе **нового** TCP-подключения с новым паролем, отказе прежнего
   и заведомо неверного. `pg_isready`, открытый порт и существующий connection pool
   не доказывают проверку пароля. При ручной проверке использовать `psql -W`
   с интерактивным скрытым вводом, без пароля в URI или `-c` SQL.
5. Обновить пароль у внешних клиентов: админские инструменты, backup jobs,
   отдельные инстансы/exporters и CI, которые helper не видит как running services.

Если пароль скомпрометирован, смена пароля не прекращает уже установленные
чужие PostgreSQL connections. Отдельно проверить `pg_stat_activity` и завершить
подтверждённые подозрительные подключения; не выполнять массовый terminate вслепую.

### 11.4. RabbitMQ: сменить после PostgreSQL

```powershell
.\scripts\docker-production-credential-migration-apply.ps1 -ProjectName $rotationProject -Component rabbitmq
.\scripts\docker-production-credential-migration-apply.ps1 -ProjectName $rotationProject -Component rabbitmq -Apply -BackupDirectory C:\iguana-private\credential-checkpoints
```

Меняется пароль существующего пользователя брокера, затем env и запущенные
клиенты. Пользователь, vhost, queues и permissions не пересоздаются. Не удалять
пользователя ради смены пароля: это отдельная операция с влиянием на разрешения.

Проверить status, повторное подключение panel/worker/bots, тестовое сообщение
от поступления до обработки. Проверить, что очереди не растут без consumers,
нет повторяющихся authentication errors, новый вход работает, старый — нет.
Успех management login дополнить проверкой реальной AMQP-доставки.
Изменить credentials у внешних producers/consumers отдельно.
Смена пароля не отзывает уже аутентифицированные AMQP connections: при инциденте
выявить и закрыть подозрительные подключения отдельно, учтя возврат unacked сообщений.

### 11.5. Redis: сменить после RabbitMQ

```powershell
.\scripts\docker-production-credential-migration-apply.ps1 -ProjectName $rotationProject -Component redis
.\scripts\docker-production-credential-migration-apply.ps1 -ProjectName $rotationProject -Component redis -Apply -BackupDirectory C:\iguana-private\credential-checkpoints
```

Helper меняет runtime `requirepass`, сохраняет пароль старта в `.env`,
пересоздаёт Redis, работающий `redis-exporter` и клиентов. Проверить status,
reconnect приложений и функции, использующие Redis, включая realtime fanout
при включённом Redis-режиме. После recreate проверить новое подключение:
с новым паролем `PING` даёт `PONG`, без аутентификации/с неверным/старым паролем
доступ отклоняется. Для ручной проверки `redis-cli --askpass` предпочтительнее
`-a <пароль>`. Не использовать `FLUSHALL`, `FLUSHDB` или удаление volume.
Сохранённый пароль старта обязателен: runtime `CONFIG SET` сам по себе его не заменяет.

Этот helper рассчитан на текущий общий `requirepass`. Если Redis переведён
на именованных ACL users, нужна отдельная процедура для ACL, а не подмена
имени пользователя или ожидание, что `requirepass` ротирует всех ACL users.

### 11.6. Grafana: где открыть и какими данными входить

В проекте Grafana — отдельный сервис observability overlay:
`docker-compose.production-observability.yml`, не раздел внутри iguana.
По умолчанию HTTP опубликован на **127.0.0.1:3000** хоста Docker.
Актуальные параметры в локальном `.env`:

- `IGUANA_GRAFANA_BIND_HOST` — адрес публикации;
- `IGUANA_GRAFANA_PORT` — порт на хосте;
- `IGUANA_GRAFANA_ADMIN_USER` — логин Grafana;
- `IGUANA_GRAFANA_ADMIN_PASSWORD` — согласованный текущий пароль после ротации.

Проверить только нужный контейнер:

```powershell
docker ps -a --filter "label=com.docker.compose.project=$rotationProject" --filter 'label=com.docker.compose.service=grafana' --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
```

Если браузер открыт **на сервере Docker**, адрес при стандартном порте:
`http://127.0.0.1:3000/login`. Если Docker на другом сервере, localhost вашего
ноутбука к нему не относится. Открыть SSH-туннель в отдельном терминале:

```text
ssh -N -L 13000:127.0.0.1:3000 ИМЯ_ОПЕРАТОРА@АДРЕС_СЕРВЕРА
```

Пока туннель работает, в браузере ноутбука открыть
`http://127.0.0.1:13000/login`. При изменённом порте сервера заменить последний
`3000`. При Docker Desktop на Windows без SSH можно открыть браузер в разрешённом
RDP-сеансе самого хоста. Сохранять loopback bind; открывать Grafana всему интернету
для входа не требуется. Не использовать пароль пользователя панели iguana.

Посмотреть нужные строки `.env` приватно в редакторе, например `notepad .env`,
не отправляя файл/скриншот в чат. Имя `admin` — лишь fallback начальной настройки,
не доказательство реальной учётной записи. Если пароль меняли только в UI,
запись в БД Grafana могла разойтись с `.env`.
После входа серверные административные функции находятся в **Administration**;
для управления пользователями нужна роль **Grafana server administrator**.
Администратор организации и серверный администратор имеют разные полномочия.
[Вход в Grafana](https://grafana.com/docs/grafana/latest/setup-grafana/sign-in-to-grafana/),
[Server administrator](https://grafana.com/docs/grafana/latest/administration/roles-and-permissions/).

Если контейнера нет — ротация выдаст ошибку. Если он stopped/unhealthy — сначала
восстановить его штатным процессом запуска overlay. Не запускать весь production
deploy с rebuild только ради проверки адреса Grafana.

### 11.7. Grafana: штатная смена и потерянный пароль

Если текущий пароль известен и preflight проходит:

```powershell
.\scripts\docker-production-credential-migration-apply.ps1 -ProjectName $rotationProject -Component grafana
.\scripts\docker-production-credential-migration-apply.ps1 -ProjectName $rotationProject -Component grafana -Apply -BackupDirectory C:\iguana-private\credential-checkpoints
```

Helper меняет persisted пароль через Grafana CLI, записывает новый
`IGUANA_GRAFANA_ADMIN_PASSWORD`, пересоздаёт Grafana и повторяет authenticated probe.
Дашборды и volume сохраняются. После этого выйти из Grafana, открыть приватное
окно браузера и войти новым паролем из `.env`. Отдельно проверить отказ старого.
Не менять пароль ещё раз только в UI без синхронизации с источником конфигурации.
Эта операция меняет выбранного admin, не пароли всех локальных пользователей Grafana.

Если текущий пароль потерян, helper с обязательным auth preflight может не пройти.
Восстановление требует административного доступа к Docker и правильной БД Grafana.
Не удалять volume и не ожидать сброса от смены `GF_SECURITY_ADMIN_PASSWORD`.
В текущем CLI предусмотрен `--password-from-stdin`; проверить поддержку на своём image:

```powershell
$grafanaIds = @(docker ps --filter "label=com.docker.compose.project=$rotationProject" --filter 'label=com.docker.compose.service=grafana' --format '{{.ID}}')
if ($grafanaIds.Count -ne 1) { throw 'Ожидался один работающий контейнер Grafana' }
$grafanaContainer = $grafanaIds[0]
docker exec $grafanaContainer grafana cli admin reset-admin-password --help
```

Если флаг поддерживается и подтверждён ID нужного server admin, выполнить скрытый ввод
временного сильного ASCII-пароля (например, случайного из password manager).
Пример использует стандартный admin ID 1; для другого администратора сначала
уточнить ID и добавить `--user-id ЧИСЛО`. Не менять ID наугад:

```powershell
$grafanaRecovery = Read-Host 'Новый временный пароль Grafana' -AsSecureString
$grafanaRecoveryPtr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($grafanaRecovery)
try {
  [Runtime.InteropServices.Marshal]::PtrToStringBSTR($grafanaRecoveryPtr) |
    docker exec -i $grafanaContainer grafana cli --homepath /usr/share/grafana --config /etc/grafana/grafana.ini --configOverrides cfg:default.paths.data=/var/lib/grafana admin reset-admin-password --password-from-stdin
  if ($LASTEXITCODE -ne 0) { throw 'Grafana CLI recovery failed' }
} finally {
  [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($grafanaRecoveryPtr)
  $grafanaRecovery.Dispose()
}
```

Это адресовано стандартному Docker layout проекта. При внешней БД/других paths
сначала проверить действующую конфигурацию, иначе CLI может сбросить пароль
в другой SQLite БД. Новый секрет кратковременно присутствует в памяти процесса,
но не передаётся в аргументах команды. Приватно записать **то же** значение
в `IGUANA_GRAFANA_ADMIN_PASSWORD` локального `.env` и централизованного secret store,
проверить вход. Далее можно выполнить штатный helper, чтобы заменить временный
пароль его случайным значением. Восстановление через CLI здесь не выполнялось.
[Официальный reset и stdin](https://github.com/grafana/grafana/blob/main/docs/sources/administration/cli.md).

### 11.8. При ошибке и после завершения

При первом failed шаге остановиться: не выполнять следующий компонент и не
запускать apply циклически. Helper пробует rollback, но это best effort.
Зафиксировать компонент, время, сообщение без секретов и состояние containers;
повторить status. Если live сменился, а recreate/запись `.env` не прошли,
согласовать **live пароль + конфигурацию + все клиенты**. Копирование старого
`.env` само по себе не откатывает persisted пароль.

После каждого успешного шага хранить новое значение в password manager/vault,
обновить внешние потребители, проверить штатный backup с новыми credentials.
После всего окна повторить status, проверить login, bot message round-trip,
очереди, realtime и Grafana dashboards. При ближайшем штатном restart/deploy
проверить сохранность auth, включая не запущенные во время rotation роли/migrator.
Checkpoint хранить ограниченное время по backup policy, затем удалить безопасно.
Ни `docker compose down -v`, ни удаление данных не являются сменой пароля.

Если это обычное обслуживание, ротировать при вводе в эксплуатацию, смене
операторов/доступов и по принятой политике. При обнаруженной утечке — немедленно
после ограничения доступа и сохранения необходимых свидетельств. Смена пароля
не исправляет CVE образа: обновление версий из начала runbook остаётся отдельным шагом.

## 12. Принудительный выход пользователей панели

После доставки нового web image и применения V100 администратор открывает
**Настройки → Пользователи панели и настройки доступа → Пользователи** (тот же блок
доступен на странице пользователей). У выбранного пользователя — кнопка
**«Завершить сеансы»**; над списком — **«Завершить все сеансы»**.
Точный путь зависит от доступной навигации, сам блок помечен `data-auth-management`.

- Доступ только у `ROLE_ADMIN` / `ROLE_PORTAL_ADMIN`; одного доступа к странице
  пользователей или логина `admin` недостаточно. Запросы требуют CSRF.
- Выбранный пользователь теряет все прежние browser sessions и remember-me cookies
  на всех web-репликах. Пароль не меняется; новый вход правильным паролем разрешён.
  Чтобы запретить и повторный вход, отдельно заблокировать аккаунт/сменить пароль.
- Общий выход включает **самого оператора**, не имеет исключения для admin и
  после подтверждения возвращает его на страницу входа. Число `affected_users`
  — количество аккаунтов, а не найденных устройств/открытых сеансов.
- В БД атомарно увеличивается `users.session_version`; snapshot хранится в
  principal и учитывается в подписи remember-me. Простое удаление cookie или
  строк Spring Session не используется как гарантия отзыва.
- Защищённые HTTP-запросы со старой версией отклоняются перед controller/method
  action. Уже выполняющееся действие нельзя отменить задним числом.
- Открытые UI/GENERAL SSE проверяют версию перед отправкой и на heartbeat
  (штатный интервал 25 секунд), отправляют `session_revoked`, закрываются;
  браузер переходит на вход. Уснувшая/офлайн-вкладка обновится после восстановления
  соединения/следующего запроса. При сбое UI SSE выполняется одна JSON-проверка
  `/api/auth/ping` на серию ошибок, с переходом на вход при 401. Повторная проверка
  разрешается после успешного `connected`; периодического HTTP keep-alive нет.
- Перезапуск web и Redis не сбрасывает отзыв. Ошибка БД даёт fail closed:
  защищённый HTTP — 503, SSE закрывается, revoke API не сообщает ложный success.
- Это сеансы **панели iguana**, не внешние API tokens, bot provider tokens,
  PostgreSQL/RabbitMQ connections или учётные записи Grafana.

API для штатного UI:
`POST /api/users/{numericId}/sessions/revoke` и
`POST /api/users/sessions/revoke-all`. Оба доступны только администратору;
произвольный username в payload не заменяет numeric ID. Успех содержит
`success`, `affected_users`, `current_user_revoked`. Ошибки: 400 invalid ID,
401 нет действующего входа при Accept JSON, 403 role/CSRF, 404 target absent,
503 БД/миграция. Audit log содержит ID оператора/цели или число аккаунтов, без credentials.

Перед rollout проверить V100 на копии БД, backup/rollback и совместимость всех
web replicas. Все реплики должны получить новую проверку версии **до использования**
команд отзыва; старый image её не выполняет. Пока старая реплика доступна,
нельзя считать общий выход гарантированным. Сохранить монотонные версии:
не обнулять колонку и не удалять её при обычном rollback. Восстановление старого
backup БД может восстановить старые значения версий; после DR выполнить общий
отзыв и при необходимости заменить remember-me key, затем проверить новый вход.
Старые сериализованные principal этого класса получают version 0; миграция
сохраняет их до первого отзыва. Более старые unbound principals требуют входа.

Текст с начальными логином/паролем и placeholder `admin` удалён со страницы
`/login`. Это не меняет существующий пароль admin: фактическую смену выполнить отдельно.

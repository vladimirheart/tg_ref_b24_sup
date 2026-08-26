# Iguana production backup and recovery contour

Актуально на `2026-08-26`. Связанная задача: `01-212`.

## 1. Назначение

Этот contour создаёт реальные backup artifacts PostgreSQL и MinIO/object storage,
публикует их в отдельный host-mounted failure domain и выполняет isolated restore
rehearsal. Мониторинг `01-198` остаётся owner'ом freshness/status и читает эти
artifacts/evidence через read-only mount в `ops-worker`.

`panel-web` backup filesystem не получает.

## 2. Failure-domain contract

Production значение `IGUANA_BACKUP_DESTINATION_DIR` обязано указывать на уже
смонтированное внешнее хранилище, например:

- NAS/SMB share;
- NFS mount;
- отдельный backup-host volume;
- filesystem gateway к внешнему object storage.

Путь внутри репозитория, основной Docker volume PostgreSQL или основной MinIO
volume не считаются disaster-recovery копией.

PowerShell/Bash helper отклоняет relative/local destination в production mode.
`-AllowLocalDestination` / `--allow-local-destination` предназначен только для smoke.

## 3. Что создаётся

### PostgreSQL

Каталог `${IGUANA_BACKUP_DESTINATION_DIR}/postgres` содержит:

- `iguana-postgres-<UTC>.dump` — `pg_dump --format=custom`;
- `.dump.sha256` — SHA-256;
- `.manifest.json` — created_at, size, checksum, database;
- `.iguana-restore-evidence.properties` — последнее успешное isolated restore evidence;
- `.iguana-restore-failure.properties` — последнее неуспешное restore attempt, пока его не перекроет успешный drill.

Перед atomic publication dump проверяется через `pg_restore --list`.

### MinIO

Каталог `${IGUANA_BACKUP_DESTINATION_DIR}/minio` содержит timestamped immutable-ish
filesystem snapshots, inventory и manifests. Backup intentionally не использует
`mc mirror --remove`: удаление primary object не должно мгновенно удалить единственную
backup-копию.

Restore rehearsal загружает snapshot в отдельный ephemeral MinIO target и проверяет:

- object count;
- checksum выборочного restored object;
- end-to-end backup-package sentinel.

## 4. Запуск contour вместе с Iguana

Чтобы `ops-worker` видел artifacts/evidence read-only, production contour запускается
с backup overlay:

```powershell
.\scripts\docker-production-up.ps1 -Backup -Observability -WebReplicas 2 -WorkerReplicas 2
```

Backup jobs сами по себе не публикуют host ports и не выполняются внутри
`panel-web`/`ops-worker`.

## 5. One-shot backup

```powershell
.\scripts\docker-production-backup.ps1 -Action backup
```

Полный backup + isolated restore rehearsal:

```powershell
.\scripts\docker-production-backup.ps1 -Action full
```

Только restore rehearsal по последним artifacts:

```powershell
.\scripts\docker-production-backup.ps1 -Action restore
```

Linux equivalents:

```bash
./scripts/docker-production-backup.sh --action backup
./scripts/docker-production-backup.sh --action full
./scripts/docker-production-backup.sh --action restore
```

## 6. Schedule baseline

Рекомендуемый стартовый production cadence:

- PostgreSQL + MinIO backup: ежедневно;
- isolated restore rehearsal: раз в неделю;
- retention PostgreSQL: 30 дней;
- retention MinIO snapshots: 14 дней.

Windows Task Scheduler должен вызывать repo-root helper, например ежедневный
`docker-production-backup.ps1 -Action backup` и отдельный weekly `-Action restore`.
На Linux тот же helper вызывается cron/systemd timer'ом. Credentials остаются в
host environment/ignored `.env`; в scheduler definition secrets не дублируются.

## 7. Monitoring integration (01-198)

`BackupReadinessMonitoringScheduler` работает только в worker role и при наличии
`/opt/iguana/backups/offhost` автоматически обеспечивает два managed monitor'а:

- `iguana-postgresql-production-backup` -> `/opt/iguana/backups/offhost/postgres/iguana-postgres-*.dump`;
- `iguana-minio-production-backup` -> `/opt/iguana/backups/offhost/minio/manifests/*.manifest.json`.

При refresh `BackupReadinessMonitoringService` импортирует companion restore evidence:

- успешный automated drill обновляет `last_restore_verified_at` и note;
- failure evidence новее последнего success переводит monitor в error;
- следующий успешный drill удаляет failure file и восстанавливает обычную freshness semantics.

## 8. Isolated restore safety

Restore targets:

- не публикуют host ports;
- используют tmpfs;
- имеют отдельные ephemeral credentials;
- не используют production PostgreSQL/MinIO как destination;
- удаляются helper'ом после rehearsal.

PostgreSQL drill дополнительно проверяет наличие `tickets`,
`flyway_schema_history` и минимальное число public tables.

## 9. RPO / RTO baseline

До накопления production статистики стартовый operational target:

- RPO: <= 24h при daily backup;
- restore evidence freshness: <= 14d (лучше weekly drill);
- RTO не объявлять достигнутым только по факту наличия dump: измерять фактическое
  время restore rehearsal и после нескольких прогонов зафиксировать реалистичный SLO.

## 10. Smoke

Smoke использует `.tmp/backup-smoke-*`, то есть локальное test-only destination,
и не является доказательством off-host DR:

```powershell
.\scripts\docker-production-backup-smoke.ps1
```

Для перевода `01-212` в `🟣` дополнительно нужен production-like запуск с настоящим
`IGUANA_BACKUP_DESTINATION_DIR`, находящимся в другом failure domain.

## 11. Secret rotation

При rotation PostgreSQL/MinIO credentials:

1. обновить ignored `.env` / host secret source;
2. проверить `docker-production-backup.ps1 -ValidateOnly`;
3. выполнить `-Action backup`;
4. выполнить `-Action restore`;
5. убедиться, что `01-198` видит fresh backup и fresh automated restore evidence.

Backup destination credentials/SMB/NFS authentication настраиваются на host mount
уровне и не должны попадать в repository files.
## MinIO backup tooling image

The upstream minio/mc container is intentionally minimal and must not be treated as a general Unix toolbox.
Iguana therefore builds docker/backup/minio-tools.Dockerfile: it copies the pinned /usr/bin/mc binary into a pinned BusyBox runtime.
The MinIO backup and restore rehearsal scripts may use standard BusyBox applets such as find, awk, sort, grep and sha256sum without depending on undocumented contents of the upstream mc image.
## Remote-to-local MinIO snapshot semantics

Remote-to-local MinIO snapshots use mc cp --recursive. The backup first writes a recursive JSON inventory from the source bucket, counts source objects, materializes the bucket into the local off-host snapshot directory, and requires source_object_count to equal local_file_count before publishing the manifest. A count mismatch fails the backup instead of publishing an incomplete snapshot.
## Pinned mc recursive-copy probe

For mc RELEASE.2025-07-21T05-28-08Z, the production source spelling is not assumed from documentation alone. The local regression finalizer seeds a root object and a nested object into an isolated bucket, verifies the source from a fresh backup container, and accepts the production mc cp --recursive source spelling only after exact path and SHA-256 validation. The selected candidate in the verified environment was: slash.
## File-backed MinIO smoke lifecycle

The local backup smoke must not pass long shell programs through Windows PowerShell native `sh -c` argument serialization. It writes LF-only UTF-8/no-BOM seed, verification and cleanup shell files into the temporary backup bind mount, validates them with `sh -n`, and executes them by path inside the MinIO backup tools container.

Before the full PostgreSQL + MinIO rehearsal, smoke verifies `smoke.txt` from a fresh MinIO tools container after another `minio-init` dependency run and executes the exact production `minio-backup` service once. That preflight requires `source_object_count=1`, `local_file_count=1`, the expected bucket name and a materialized `smoke.txt` snapshot before full smoke begins.

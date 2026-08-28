# 2026-08-28 — storage cutover runtime recovery and label discovery

## Incident observed during cutover pre-gate

The storage cutover itself had not yet mutated `.env` or recreated runtime containers. The authoritative gate exposed that the production contour was mostly stopped.

Observed production state before recovery:

- `postgres` and `rabbitmq` had exited cleanly around 12:17 UTC with exit code 0.
- PostgreSQL logs showed an explicit fast shutdown request followed by a clean database shutdown, not a database crash.
- Most remaining long-running production and observability containers were stopped around 12:31 UTC.
- `docker compose ls` showed only one running container for project `tg_ref_b24_sup`.
- `db-migrate` and `minio-init` remained exited/0 as expected for one-shot services.

The operator restored existing containers with `docker start` only, without Compose recreate, build, `.env` changes, migrations, volume mutation, or `--remove-orphans`.

Post-recovery state reported GREEN:

- postgres: running / healthy
- rabbitmq: running / healthy
- redis: running / healthy
- minio: running
- ops-worker: running / healthy
- panel-web: running / healthy
- panel-direct: running / healthy
- observability long-running services: running
- db-migrate: exited / 0 (expected)
- minio-init: exited / 0 (expected)

## Cutover helper hardening

`scripts/docker-production-storage-disable-fallback.ps1` no longer uses `docker compose ps -q` to discover panel runtime replicas.

The helper now:

- discovers running `ops-worker` and `panel-web` containers through raw `docker ps` Compose labels;
- resolves the active Compose project name from `docker inspect` JSON labels;
- scopes runtime container discovery by both project and service labels;
- uses the discovered project name explicitly for targeted Compose recreate;
- verifies recreated container health and fallback environment through `docker inspect` JSON;
- retains the existing pre/post authoritative gates, `.env` backup, scale preservation, targeted `--no-deps --force-recreate`, and no-purge safety behavior.

The source contract was updated to require label-based runtime discovery and prevent reintroduction of Compose-`ps` discovery in the helper.

## Separate application defect noted

PostgreSQL logs also exposed existing application SQL using `settings_parameters.is_deleted = 0` while the PostgreSQL column is boolean. This is a separate application compatibility defect and was not the cause of the clean PostgreSQL shutdown. It is not part of the storage cutover mutation.

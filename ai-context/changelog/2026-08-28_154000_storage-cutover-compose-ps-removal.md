# 2026-08-28 — storage cutover gate: remove unreliable Compose ps precheck

## Production observation

On the Windows production operator host, `docker compose exec -T panel-web printenv APP_STORAGE_OBJECT_LEGACY_LOCAL_FALLBACK_ENABLED` returned the expected runtime value, while `docker compose ps --status running -q postgres` returned an empty result for the same Compose project. The authoritative storage cutover gate therefore produced a false `Required service is not running: postgres` failure before any data checks.

## Change

- Removed `Get-RunningServiceContainerIds` and the `docker compose ps --status running -q <service>` precheck from `scripts/docker-production-storage-cutover-gate.ps1`.
- The gate now relies on the immediately following read-only runtime operations as the service availability check:
  - `docker compose exec -T postgres printenv ...`;
  - `docker compose exec -T minio printenv ...`;
  - `docker compose exec -T panel-web printenv ...`;
  - PostgreSQL `psql -Atc` queries for the actual audit data.
- No storage mutation, database mutation, container recreate, or fallback change was added.
- Source contract now prevents reintroducing `Get-RunningServiceContainerIds` / `compose ps --status` into the authoritative gate.

## Safety

The failed production attempt occurred before `.env` mutation and before runtime recreate. Effective `panel-web` fallback was still confirmed as `true` immediately before the attempt.

# Storage cutover Windows native stdout hotfix

Date: 2026-08-28

## Live symptom

The self-contained storage cutover gate passed `-ValidateOnly`, but its first live service-state check reported `Required service is not running: postgres` even though the same production contour accepted `docker compose exec -T postgres ...` and `panel-web` was running with legacy fallback still enabled.

The failed standalone gate did not modify `.env`, runtime containers, PostgreSQL, MinIO, or local files.

## Root cause

Windows PowerShell/native process output is not guaranteed to arrive as one PowerShell object per console line. Docker Compose output can be delivered as a single string containing embedded CR/LF sequences. Exact array membership checks and per-item Docker-ID filters therefore produced false negatives.

## Fix

- `scripts/docker-production-storage-cutover-gate.ps1`
  - added explicit native-output line normalization;
  - checks `postgres`, `minio`, and `panel-web` independently via `docker compose ps --status running -q <service>`;
  - accepts only Docker-like hexadecimal container IDs;
  - keeps the gate read-only and self-contained;
  - also uses normalized native lines for PostgreSQL rows and runtime env reads.
- `scripts/docker-production-storage-disable-fallback.ps1`
  - uses the same CR/LF normalization for replica discovery, health output, runtime env inspection, and nested operator-script output.

No storage copy/delete, database mutation, image rebuild, `compose down`, or `--remove-orphans` behavior was introduced.

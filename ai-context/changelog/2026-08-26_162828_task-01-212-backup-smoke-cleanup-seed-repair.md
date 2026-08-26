# 01-212 backup smoke cleanup/seed runtime repair

## User prompt / runtime evidence

The user ran the full Docker backup smoke after the MinIO tooling repair.

Observed runtime:
- PostgreSQL backup GREEN.
- PostgreSQL isolated restore GREEN.
- MinIO backup reached GREEN but reported files=0.
- MinIO isolated restore reached GREEN but reported objects=0.
- cleanup then failed in Windows PowerShell 5.1 with NativeCommandError while docker compose rm emitted a normal "Container ... Stopping" progress message to stderr.

## Root cause

Two independent smoke/runtime issues remained:
1. the smoke seed used mc pipe without verifying that smoke.txt actually existed before backup;
2. native Docker stderr was treated as a terminating PowerShell error because ErrorActionPreference=Stop was active around redirected cleanup commands.

## Change

- seed smoke.txt with a temporary file + mc cp;
- immediately verify the seeded object with mc stat;
- run restore-target cleanup with ErrorActionPreference=Continue and decide success from LASTEXITCODE;
- apply the same rule to best-effort MinIO smoke-bucket cleanup.

## Status

01-212 remains YELLOW pending a complete GREEN backup/restore smoke and real off-host DR proof.

No stage/commit/push/reset/checkout/clean is performed by the repair helper.
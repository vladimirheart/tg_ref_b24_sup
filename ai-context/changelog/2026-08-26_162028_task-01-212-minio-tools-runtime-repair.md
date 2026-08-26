# 01-212 MinIO backup tooling runtime repair

## User prompt / runtime evidence

The user ran scripts/docker-production-backup-smoke.ps1 and reported:

- /tmp/minio-backup.sh: line 42: find: command not found
- /tmp/minio-backup.sh: line 43: awk: command not found
- docker compose command failed with exit code 127: run --rm minio-backup

## Change

- add docker/backup/minio-tools.Dockerfile;
- copy the pinned MinIO mc binary into a pinned BusyBox runtime;
- use the tools image for MinIO backup and restore rehearsal jobs;
- force image build before MinIO backup/restore and before smoke seeding;
- add source-contract coverage and runtime tool probing;
- document why upstream minio/mc must not be treated as a Unix toolbox.

## Status

01-212 remains YELLOW until the full Docker backup/restore smoke and real off-host DR proof pass.
No commit or push is performed by this repair.

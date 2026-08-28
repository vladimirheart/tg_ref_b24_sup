# Guarded storage fallback cutover helper

Date: 2026-08-28

## Context

The authoritative storage cutover gate is GREEN with 72 attachment mappings checked, 20 reviewed known-unrecoverable historical attachments, zero unexpected missing dialog objects, zero missing metadata rows, zero missing panel avatars, and zero invalid panel avatar refs. The client-avatar cutover audit is also GREEN with no client avatar history rows currently present. Manual UI/media spot-checks were reported as looking OK.

## Change

Added `scripts/docker-production-storage-disable-fallback.ps1` to perform the final fallback cutover without invoking the broad production launcher.

The helper:

- supports `-ValidateOnly`;
- re-runs the authoritative storage gate and client-avatar audit before any mutation;
- refuses a conflicting process-environment override for `APP_STORAGE_OBJECT_LEGACY_LOCAL_FALLBACK_ENABLED`;
- creates a timestamped backup of `.env` and persists `APP_STORAGE_OBJECT_LEGACY_LOCAL_FALLBACK_ENABLED=false` as UTF-8 without BOM;
- preserves the currently running `ops-worker` and `panel-web` replica counts;
- filters `docker compose ps -q` output to real Docker hex IDs so Compose stderr/orphan notices cannot contaminate replica counts;
- recreates only `ops-worker` and `panel-web`, with `--no-deps --force-recreate`, and does not use `--remove-orphans`;
- waits for runtime health and verifies the fallback environment variable inside every recreated runtime container;
- re-runs both read-only cutover gates after recreate;
- does not recreate MinIO, PostgreSQL, RabbitMQ, Redis, panel-direct, bots, observability, or backup services.

A source-contract test locks down the intended scope and deletion-free behavior.

## Safety

The helper does not delete legacy files or MinIO objects and does not modify PostgreSQL data. The `.env` backup path is printed before runtime recreation. Physical purge remains a later, separate manual step after post-cutover UI validation and rollback-window confirmation.

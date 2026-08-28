# Storage fallback helper PowerShell 5.1 parser fix

Date: 2026-08-28

## Context

Production operator validation of `scripts/docker-production-storage-disable-fallback.ps1` on Windows PowerShell 5.1 failed before execution with an invalid variable reference error.

The offending interpolation was:

`$ContainerId:`

PowerShell 5.1 interprets the colon as part of a scoped/drive-style variable reference.

## Fix

Changed the diagnostic interpolation to:

`${ContainerId}:`

No runtime behavior, Compose command, storage mapping, database state, MinIO object, or `.env` behavior was changed.

## Guard

`DockerProductionStorageDisableFallbackSourceContractTest` now requires `${ContainerId}:` and rejects `$ContainerId:` so the PowerShell 5.1 parser regression cannot silently return.

## Production state

The parser failure occurred before the cutover helper was executed. The restored production contour remained running/healthy and fallback cutover had not yet begun.

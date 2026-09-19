# 01-212 — ValidateOnly destination validation is read-only

## Trigger

Before the deferred real SMB/off-host acceptance, source review found that `docker-production-backup.ps1 -ValidateOnly` and `docker-production-backup.sh --validate-only` still created and deleted a temporary destination write-probe file.

## Change

- PowerShell destination resolution now receives explicit `RequireWriteAccess`; `ValidateOnly` passes false.
- Bash wraps the temporary destination write/delete probe so it runs only for non-validation execution.
- Actual `backup`, `restore` and `full` paths keep the write/delete preflight unchanged.
- Source-contract tests and the production backup runbook document the read-only validation boundary.

## Safety

No production SMB values, credentials, backup/restore execution, Docker mutation or service restart are performed by this source-only slice. `01-212` remains YELLOW pending real off-host DR proof.

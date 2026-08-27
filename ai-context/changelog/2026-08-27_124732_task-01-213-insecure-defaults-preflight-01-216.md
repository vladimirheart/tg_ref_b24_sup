# 01-213 - fix AllowInsecureDefaults missing-value preflight and create 01-216

## Trigger prompt

> не, давай править ошибку для начала, а эту задачу оформи как отдельную и выдели название капсом, чтобы она бросалась в глаза

## Runtime trigger

The real `01-213` production-observability startup was attempted with:

`docker-production-up.ps1 -Observability -Build -AllowInsecureDefaults`

and failed with:

`PostgreSQL password must be configured (IGUANA_POSTGRES_PASSWORD).`

## Root cause

`-AllowInsecureDefaults` only disabled the rejection of known weak values. It still required every secret/default-backed setting to be explicitly present in process environment or `.env`.

That contradicted the intended local compatibility behavior because Docker Compose already has documented local defaults for PostgreSQL, RabbitMQ, Redis, MinIO and Grafana.

## Fix

- PowerShell and Bash launchers now materialize missing documented insecure defaults in the launcher process environment only when the insecure flag is explicit.
- Existing environment or `.env` values are never overwritten.
- No `.env` is created or modified.
- Existing `config/shared/monitoring-credentials.key` is preferred through the `base64:` compatibility bridge for `MONITORING_CREDENTIALS_MASTER_KEY`.
- Normal secure mode is unchanged and continues rejecting missing/default secrets.
- Added source-contract coverage.
- Added separate red task `01-216` for the full secure first-run/bootstrap and existing persistent-volume migration workflow.

## Verification

The apply performs:
- PowerShell parser verification;
- Bash syntax verification when Bash is available;
- Maven test-compile and targeted source-contract tests;
- the exact failing scenario as non-starting validation:
  `docker-production-up.ps1 -Observability -ValidateOnly -AllowInsecureDefaults`;
- `git diff --check`.

No Docker service is started by the apply.

No git add/commit/push/reset/checkout/clean is performed.

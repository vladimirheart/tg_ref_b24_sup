# 01-213 - Alertmanager -> Iguana incident delivery boundary

## Trigger prompts

Initial task continuation:

> норм.
> давай дальше по целевой задаче проекта

Correction trigger:

> будь внимательнее!

The first v1 apply stopped at its first HEAD precheck and therefore made no task-01-213 repository changes.

## Base

Reviewed repository base for v1.1:

`4ff59fceb8c9cb767c5706497051484f11f04757`

This commit is exactly one commit above `ba7a059d...` and contains the completed task-01-212 panel-lifecycle backup-runner changes. The v1.1 precheck no longer requires HEAD equality: the reviewed base must be an ancestor, and every tracked file touched by 01-213 must still have the reviewed Git blob and no local modifications. Unrelated newer commits therefore do not break the apply, while relevant changes fail closed.

## Architecture

- Alertmanager remains aggregation/routing only.
- high/critical webhook events go to an internal-only panel-web endpoint.
- Bearer credential is supplied from an ignored dedicated host secret file using Alertmanager `credentials_file`; no secret is committed to YAML.
- public nginx configs deny `/internal/observability/**`.
- Alert fingerprint is the Iguana signal key.
- firing/resolved transitions are idempotent against active signal incidents.
- a shared Redis lease protects scaled panel-web replicas.
- newly created signal incidents receive an approved route before initial outbox enqueue.
- actual delivery stays in the existing durable `incident_route_delivery_outbox`.

## v1.1 correction hardening

- Windows PowerShell 5.1 token generation uses `BitConverter`, not the unavailable `[Convert]::ToHexString`.
- token bootstrap receives the effective `IGUANA_SECRETS_DIR` used by Compose, including `.env`.
- the token is bind-mounted read-only into both panel-web and Alertmanager from a dedicated ignored secret directory; Unix keeps the host directory `0700` and token `0644` so Alertmanager can read it as `nobody`.
- Alertmanager webhook model explicitly accepts `routeLabels` and `notification_reason`.
- a versioned local `severity=medium` `IguanaAlertmanagerDeliveryFailed` Prometheus rule exposes webhook failures before the Iguana outbox boundary without recursively routing that fallback signal through the failed webhook.
- reviewed target Git blobs are checked before any 01-213 write.

## Verification included

- Java test compile;
- Alertmanager guard tests;
- Alertmanager ingestion transition tests;
- production Alertmanager source-contract test;
- PowerShell parser checks for secret bootstrap and E2E smoke;
- Bash syntax checks when Bash is available;
- `git diff --check`.

The real Docker firing/resolved E2E smoke is intentionally not run by the apply because the operator stated the project is stopped. Task 01-213 remains YELLOW until that smoke is executed against a running production observability contour.

No git add/commit/push/reset/checkout/clean is performed.

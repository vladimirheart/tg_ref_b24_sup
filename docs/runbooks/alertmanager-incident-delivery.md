# Alertmanager -> Iguana incident delivery

Task `01-213` connects production Alertmanager to the existing Iguana incident/notification delivery boundary.

## Boundary

Alertmanager does **not** contain Telegram/VK/MAX/SMTP credentials.

Only alerts with `severity=high|critical` are sent to:

`http://panel-web:8080/internal/observability/alertmanager`

The endpoint is reachable on the internal Docker network. All supported nginx ingress configs explicitly return `404` for `/internal/observability/**`.

Authentication policy:

- Alertmanager uses `Authorization: Bearer ...`;
- the credential is read from `/run/secrets/iguana-alertmanager-ingestion.token`;
- the same host token is bind-mounted read-only into both `panel-web` and `alertmanager`;
- `alertmanager` starts only after `panel-web` reports healthy in the production observability compose;
- the default host source is ignored `config/secrets/alertmanager-ingestion.token`;
- `IGUANA_SECRETS_DIR` may point to another host secret directory;
- the token is never embedded in `alertmanager.yml` or `.env`;
- no request HMAC is used because Alertmanager's generic webhook sender does not provide the dynamic canonical-signature contract used by Iguana's bot API. The approved compensating boundary is internal-network-only routing + a random Bearer credential file + public nginx deny rules.

## Bootstrap

Normal production observability startup creates the token automatically if it is absent:

Windows:

```powershell
.\scripts\docker-production-up.ps1 -Observability -Build
```

Linux/Unix:

```bash
bash ./scripts/docker-production-up.sh --observability --build
```

Manual bootstrap is also available:

```powershell
.\scripts\ensure-alertmanager-ingestion-token.ps1
```

```bash
bash ./scripts/ensure-alertmanager-ingestion-token.sh
```

The secret value is never printed. The launcher resolves the same effective `IGUANA_SECRETS_DIR` used by Compose, including a value supplied through repository `.env`. On Unix the helper keeps the secret directory mode `0700` while the bind-mounted token file is `0644`, so Alertmanager can read it as its non-root `nobody` user without exposing the directory to other host users.

## Event model and idempotency

Each Alertmanager alert is normalized using:

- `status`
- `fingerprint`
- `labels`
- `annotations`
- `startsAt`
- `endsAt`
- `generatorURL`

Iguana uses:

- `signal_type=alertmanager`
- `signal_key=<Alertmanager fingerprint>`

Transition contract:

- first `firing` -> create signal incident + approved route;
- repeated `firing` while that incident is active -> no-op/deduplicated;
- first `resolved` -> resolve active signal incident;
- repeated `resolved` -> no-op/deduplicated;
- later firing after a completed resolved cycle -> a new incident cycle.

A short shared Redis lease protects each fingerprint across scaled `panel-web` replicas. If a lease cannot be acquired, the endpoint returns a retryable failure instead of processing concurrently.

## Delivery path

New Alertmanager incidents receive the configured approved incident route; default:

`all_operators -> all_operators`

The route is queued through the existing `incident_route_delivery_outbox`.

That means existing Iguana behavior remains authoritative for:

- retry;
- delivery status;
- failed/stale processing recovery;
- audit payload;
- operator diagnostics;
- `IguanaIncidentDeliveryFailed` / `IguanaIncidentDeliveryStale` Prometheus alerts.

No provider-specific sender is implemented in the Alertmanager integration.

## Metrics

The panel exposes bounded ingestion counters through Micrometer/Prometheus:

`iguana_alertmanager_ingestion_events_total{status=...,outcome=...}`

Outcomes are bounded to:

- `opened`
- `resolved`
- `deduplicated`
- `ignored`
- `deferred`

Alertmanager delivery failures are also surfaced by the versioned Prometheus rule `IguanaAlertmanagerDeliveryFailed`, based on `alertmanager_notifications_failed_total{integration="webhook"}`. The fallback rule is intentionally `severity=medium`, so it remains visible locally in Prometheus/Alertmanager without recursively attempting the same broken high/critical webhook path. It catches failures that occur before Iguana can create an incident/outbox row, such as `401`/`503` or network errors.

## End-to-end smoke

Prerequisite: production contour + observability are already running with the current code/config.

Run:

```powershell
.\scripts\docker-alertmanager-delivery-smoke.ps1
```

The smoke:

1. writes a temporary Prometheus rule with `vector(1)`;
2. reloads Prometheus;
3. waits for the alert in Prometheus;
4. waits for the same alert in Alertmanager and reads its fingerprint;
5. waits for an Iguana `alertmanager` signal incident;
6. requires `incident_signal_updated` outbox delivery to become `delivered`;
7. flips the rule to `vector(0) == 1`;
8. reloads Prometheus;
9. waits for the incident to become `resolved`;
10. requires `incident_signal_resolved` outbox delivery to become `delivered`;
11. removes the temporary rule and reloads Prometheus.

Successful final marker:

`ALERTMANAGER -> IGUANA FIRING/RESOLVED E2E GREEN`

## Secret rotation

1. stop/restart only Alertmanager delivery traffic or perform during a maintenance window;
2. atomically replace `${IGUANA_SECRETS_DIR:-./config/secrets}/alertmanager-ingestion.token` with a new random value of at least 32 characters;
3. keep the file outside Git and restricted to the host/service account;
4. restart Alertmanager so its credential file is reloaded;
5. panel-web does not require a restart because the guard reads the token file for each request;
6. run the E2E smoke.

Do not copy the token into Alertmanager YAML, screenshots, logs or changelog.

## Failure modes

- token file missing/invalid: Iguana returns `503`; Alertmanager retries according to its webhook behavior;
- Bearer mismatch: Iguana returns `401`;
- endpoint disabled: Iguana returns `404`;
- Redis lease unavailable in full production: event is not processed concurrently; request fails so Alertmanager can retry;
- incident route delivery failure: row remains visible in existing delivery health/incident diagnostics and retries through the existing outbox;
- public request to `/internal/observability/**`: nginx returns `404`.

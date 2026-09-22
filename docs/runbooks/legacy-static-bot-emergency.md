# Legacy static bot emergency runbook

Task: `01-242`
Date: 2026-09-22

## Purpose

Normal production owns every active Telegram, VK and MAX channel through the single `bot-runner` supervisor. The canonical file `docker-compose.production-contour.yml` does not contain static bot services.

The separate `docker-compose.production-legacy-bots.yml` exists only for emergency compatibility diagnostics or a controlled temporary fallback. It must never be used as a second runtime owner while `bot-runner` is running.

## Supported entrypoints

Use only the guarded helpers:

PowerShell:

```powershell
.\scripts\docker-production-legacy-bots.ps1 -Action status -Bot telegram
.\scripts\docker-production-legacy-bots.ps1 -Action start -Bot telegram -ConfirmEmergencyMode -Build
.\scripts\docker-production-legacy-bots.ps1 -Action stop -Bot telegram
```

Bash:

```bash
./scripts/docker-production-legacy-bots.sh --action status --bot telegram
./scripts/docker-production-legacy-bots.sh --action start --bot telegram --confirm-emergency-mode --build
./scripts/docker-production-legacy-bots.sh --action stop --bot telegram
```

Replace `telegram` with `vk` or `max` when required.

Do not start the emergency services with a raw `docker compose ... up bot-*` command. The guarded helpers are part of the ownership contract.

## Start guard

Before any emergency start the helper:

1. resolves the active Compose project;
2. verifies that `bot-runner` has zero running containers;
3. verifies that `postgres`, `rabbitmq`, `redis`, `minio` and `panel-web` are already running;
4. validates the combined normal + emergency Compose model;
5. starts only the selected static bot with `--no-deps`;
6. checks `bot-runner` again after the start;
7. if a runner appeared concurrently, immediately stops/removes the selected legacy service and returns BLOCK.

The explicit `-ConfirmEmergencyMode` / `--confirm-emergency-mode` flag is mandatory for start.

## Stop guard

The stop action stops and removes only the selected legacy service. It never stops `panel-web`, `ops-worker`, infrastructure or `bot-runner`.

After emergency diagnostics, always stop/remove the legacy service before returning to the normal production launcher.

## Normal production launcher interaction

`scripts/docker-production-up.ps1` and `.sh` refuse normal production start when any running `bot-telegram`, `bot-vk` or `bot-max` container exists in the same Compose project. This prevents a transition that could briefly create mixed runtime ownership.

Stop the emergency runtime first, then run the normal production launcher. Normal production remains `bot-runner=1`.

## Credential rotation interaction

Credential rotation keeps the same mutually exclusive runtime rule. If an emergency static bot is the current owner and `bot-runner` is absent, the credential rotation scripts add `docker-compose.production-legacy-bots.yml` to the recreate model so the selected legacy service can be recreated without reintroducing it into the canonical production compose.

If both ownership contours are detected, credential rotation blocks before changing credentials.

## Acceptance

A valid topology has exactly one of these states:

- normal: `bot-runner` running and all static legacy bot services stopped;
- emergency: `bot-runner` stopped and one or more explicitly started static legacy bot services running;
- maintenance: both contours stopped.

`bot-runner` plus any running static legacy bot is always BLOCK.

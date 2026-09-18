# 2026-09-18 — task 01-236: legacy static runtime resurrection guard

## Пользовательский вывод

> legacy_restart_policy=unless-stopped
> Legacy TELEGRAM_BOT_TOKEN is empty

## Read-only evidence

- Production has one healthy dynamic `bot-runner` and one simultaneously running legacy `bot-telegram`.
- Static bot profiles are already marked `legacy-static-bots-disabled`, but their compose services still use `restart: unless-stopped`.
- The legacy container was created earlier and started again with the infrastructure after a later Docker/host restart.
- Its Telegram token environment is empty; bot source maps that to a placeholder rather than loading a real channel token from PostgreSQL/shared config.

## Safety correction

- Legacy static bot services switch to `restart: "no"` so Docker daemon restart cannot resurrect an old emergency runtime automatically after it has been recreated with current compose metadata.
- PowerShell and Bash credential rotation fail fast if `bot-runner` and any legacy static bot service are simultaneously running.
- Bot restart choreography is mutually exclusive: active `bot-runner` wins; legacy static services are restart targets only in a supervisor-absent emergency contour.
- Source-contract tests and the rotation runbook protect this topology.

## Runtime boundary

- This source slice does not stop/remove containers, change Docker restart policy of the already-created live legacy container, rotate credentials, edit `.env`, restart services or deploy.
- The existing `bot-telegram` container requires a separate guarded runtime cleanup after this source slice is accepted.

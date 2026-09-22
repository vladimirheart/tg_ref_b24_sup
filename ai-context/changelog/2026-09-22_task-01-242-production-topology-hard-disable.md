# 01-242 production topology hard-disable

Date: 2026-09-22

## Evidence before source slice

- Controlled `01-242` candidate rollout completed GREEN without automatic rollback.
- Live duplicate acceptance completed GREEN for Telegram delivery guard, MAX synthetic duplicate handling, active-message durable boundary and ticket-created durable boundary.
- PostgreSQL acceptance writes were enclosed in a transaction and rolled back.
- Synthetic Redis delivery keys were cleaned.
- Runtime preservation remained GREEN and task status stayed `🟡`.

## Source topology change

- Remove `bot-telegram`, `bot-vk` and `bot-max` from `docker-compose.production-contour.yml`.
- Move the three static compatibility services into `docker-compose.production-legacy-bots.yml`.
- Add guarded PowerShell/Bash emergency launchers that require explicit confirmation and refuse to start while `bot-runner` is running.
- Add a second post-start ownership check; if `bot-runner` appears concurrently, the helper stops/removes the just-started legacy service and returns BLOCK.
- Make normal `docker-production-up.*` refuse startup while any emergency static bot is running.
- Keep credential-rotation recovery compatible by adding the emergency compose only when a legacy static runtime is the current owner.
- Update source-contract tests and production/runtime documentation to make `bot-runner` the only normal production bot owner.

## Non-goals

- No Docker container is recreated by the source apply.
- No production bot is started or stopped by the source apply.
- No database or `.env` mutation is performed.
- No Git stage/commit/push is performed by the source apply.

Task remained `🟡` at source-apply time until targeted tests, exact commit/push and final read-only topology verification were complete.

## Final verification

- Targeted topology tests and both Compose contracts completed GREEN.
- Topology commit `9e9040e37b81b945b6ac2d499230fa990de79e53` was pushed from parent `fc4b63182aa1de37aa4ad6dea2ed7641b48935ee` after exact staging and a fresh remote guard.
- Independent GitHub verification confirmed exactly 18 expected changed files, `ahead_by=1`, `behind_by=0`.
- Final read-only production topology acceptance completed GREEN with `bot-runner` as the sole owner, 3 child runtimes, zero legacy static services running, restart-policy guard GREEN, mixed ownership false and current child logs GREEN.
- No runtime or DB mutation was performed by the final topology acceptance.
- Task `01-242` is now `🟣`: AI work complete, awaiting manual user acceptance. Only the user may mark it `🟢`.

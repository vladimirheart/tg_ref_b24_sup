# 2026-09-19 19:05:00 - task 01-241 bot-runner lifecycle command routing

## Change

- Routed manual bot lifecycle from `panel-web` to the single dynamic `bot-runner` instead of attempting local child-process ownership.
- Added an internal signed/idempotent lifecycle endpoint and a panel-side client that validates command acknowledgement identity.
- Added production settings for the internal runner API URL/timeout.
- Added controller/client regression tests and synchronized the bot runtime contract documentation.

## Safety

- Source-only slice.
- No runtime restart, child bot start/stop, DB migration, backup/restore, storage mutation, credential rotation or external-system mutation is performed by the operator.
- Task 01-241 remains yellow until rollout and manual acceptance.

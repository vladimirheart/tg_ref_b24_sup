# 01-210 credential rotation incident workbench completion

Date: 2026-08-25 18:13 +03:00
Task: 01-210

## User prompt

«сделай»

Контекст: завершить найденные недоделки задачи 01-210 после аудита свежего репозитория.

## Changes

- Added normalized `signal_context` to incident summary/detail payloads from the latest relevant signal event.
- Added credential rotation context fields for reason, severity policy/reason, next action and diagnostic metadata.
- Added `incident_context_version` and one-time refresh of already-active credential rotation incidents with old context.
- Added dedicated credential rotation explanation block to incidents workbench.
- Header cause now prefers normalized credential rotation reason.
- Added UI static contract test and expanded service assertions.
- Task 01-210 moves to purple only after targeted verification succeeds.

## Manual verification still required

Open a real credential_rotation incident and confirm:
- header reason is meaningful;
- the separate explanation block is visible;
- warning/critical policy is understandable;
- recommended action matches the current registry condition;
- an already-open old incident refreshes after registry sync.

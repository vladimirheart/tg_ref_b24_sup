# 2026-09-01 07:07:00 - task 01-216 secure bootstrap closeout

## User prompt

> делай дальше по задачам

## Summary

- Audited the implemented fresh-install bootstrap slice for `01-216` and its explicit boundary with persisted credential migration task `01-222`.
- Found that `-Force` could previously regenerate `.env` above initialized Docker volumes after only a warning.
- Added fail-closed PostgreSQL/RabbitMQ persistent-volume detection to both `bootstrap-first-run.ps1` and `bootstrap-first-run.sh`.
- Bootstrap now refuses before secret generation or `.env` writes and directs the operator to the controlled migration runbook.
- Added source-contract assertions for both platform helpers.
- Moved `01-216` from `🟡` to `🟣`; component credential rotations remain owned by `01-222` and follow-up tasks.

## Verification

- PowerShell parser: passed.
- Bash parser: passed.
- `BootstrapFirstRunSecretsSourceContractTest`: 2 tests passed.
- Live existing-volume rehearsal: `bootstrap-first-run.ps1 -Force` refused after detecting initialized PostgreSQL/RabbitMQ volumes; `.env` remained absent.

## Files

- `scripts/bootstrap-first-run.ps1`
- `scripts/bootstrap-first-run.sh`
- `spring-panel/src/test/java/com/example/panel/runtime/BootstrapFirstRunSecretsSourceContractTest.java`
- `ai-context/tasks/task-list.md`
- `ai-context/tasks/task-details/01-216.md`

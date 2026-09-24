# 01-274 notification recipient boolean compatibility hotfix

Date: 2026-09-24
Task: 01-274

## Production evidence

Read-only runtime diagnostic R27 showed that task persistence had progressed beyond the previous timestamp blocker, but notification recipient resolution failed in PostgreSQL with `COALESCE types boolean and integer cannot be matched`. The failing path was `NotificationService.loadOperatorRecipients()`, which compared boolean columns with numeric fallback literals.

## Root cause

The operator lookup was written for legacy numeric-boolean behavior: `COALESCE(enabled, 1) = 1` and `COALESCE(is_blocked, 0) = 0`. PostgreSQL stores these flags as native boolean values and rejects boolean/integer COALESCE expressions. Catching the JDBC exception did not make the operation safe because PostgreSQL had already aborted the surrounding task transaction.

## Fix

- The query now selects optional `enabled` / `is_blocked` columns without coercing them in SQL.
- Boolean filtering is performed in Java and accepts native Boolean, legacy numeric 0/1 and common string encodings.
- Null semantics are preserved: missing `enabled` means enabled, missing `is_blocked` means not blocked.
- Added a regression contract preventing the old PostgreSQL-incompatible COALESCE expressions.
- No DB migration or runtime mutation is part of the source apply/validation package.

Task remains YELLOW pending checkpoint, targeted panel-web rollout and manual task save/reload acceptance.

# 01-278: S2A exact legacy ticket ID guard (R10)

- Date: 2026-10-11.
- User prompt: "я обновил репо другими изменениями. проанализируй и продолжи"; R9 subsequently confirmed local owner draft without GitHub/private data exposure.
- Baseline: `6347dbc1e14cb0b24afc23fefffb28ce6de5cea7`.
- Scope: `LegacyTicketIdJdbcGuard`, PostgreSQL regression test, `ai-context/tasks/task-details/01-278.md`, this append-only changelog.
- Behavior: JDBC uniqueness requires an exact legacy ticket ID without whitespace normalization; blank or ambiguous IDs remain fail-closed.
- Verification plan: detached worktree, repository Maven wrapper `clean -Dtest=LegacyTicketIdJdbcGuardTest test`, exactly four source files; full Maven result requires Windows R10 validate log.
- Exclusions: no production DB writes, V51/V52 migration, alias assignment, backfill, runtime changes, stage, commit, push or rollout.
- S2B private record decisions/product approvals remain pending.

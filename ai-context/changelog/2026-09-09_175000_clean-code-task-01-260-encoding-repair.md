# 2026-09-09 — 01-260 task encoding repair

## Scope

- repaired the corrupted tail of `ai-context/tasks/task-details/01-260.md`;
- no source/runtime behavior changed;
- no unrelated spelling cleanup was mixed into this repair.

## Recovery basis

- the corruption was already present in the first Git commit that introduced the task file, so the exact original tail is not recoverable from repository history;
- the repaired wording is intentionally conservative and follows `docs/SOURCE_LAYOUT_POLICY.md`: size thresholds are review triggers, not automatic hard-fail rules;
- the repaired item keeps the existing requirement for an automatic size/regression report while making explicit that existing baseline oversize does not fail the build.

## Validation

- baseline task blob is pinned before mutation;
- only the corrupted final plan line is replaced; all preceding task bytes remain unchanged;
- the repaired file must contain no replacement characters or C0 control bytes except normal line endings;
- `git diff --check` must pass;
- no staging, commit, push or deployment is performed by the source operator.

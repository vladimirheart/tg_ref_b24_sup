# 01-212 - lifecycle source-contract canonicalization v1.3

## Trigger

Operator feedback after v1.2:

> чувак, будь внимательнее

v1.2 stopped before modifying repository files because its own PowerShell preflight searched for a literal backtick before `$Action`. The real runtime contains `"-Action", $Action` and `"-Mode", $Mode`.

## Root cause

The previous repair strategy remained too brittle:
- line-level assertion replacement;
- hand-written exact marker guards;
- an escaping bug in a single-quoted PowerShell string.

## Resolution

v1.3 deliberately simplifies the repair:
- no runtime regeneration;
- no `$Action/$Mode` preflight string matching;
- replace the two affected JUnit test methods in full;
- canonical methods assert both PowerShell and Bash lifecycle behavior;
- canonical manual method asserts legacy installer scripts are removal-only, not periodic scheduler installers;
- verify the old Windows minute Scheduled Task is still absent;
- run Maven test-compile + targeted tests;
- run `git diff --check`.

The panel stays stopped and the lifecycle daemon is not started.

No git add/commit/push/reset/checkout/clean is performed.

# Clean-code/source-layout - P7 automatic source size regression report

Date: 2026-09-17
Task: 01-260
Baseline: 94f42bf30fd48419c53515c7c0fef87f91cf97be

## Scope

Close the source-layout migration with an automatic, non-blocking size/regression review signal based on the thresholds already documented in `docs/SOURCE_LAYOUT_POLICY.md`.

## Changes

- add `tools/source-layout-report.ps1` for SCSS, browser JS, Thymeleaf, Java and operational-script review thresholds;
- normalize line endings before UTF-8 byte measurement so Windows/Linux checkouts produce the same metrics;
- add `tools/source-layout-baseline.json` containing the accepted review-triggered baseline at this commit;
- distinguish new threshold crossings, growth regressions, baseline carry-over and improvements;
- keep normal `NEW`/`REGRESSION` findings non-blocking while treating report execution/configuration errors as tool failures;
- invoke the report automatically from `tools/release-readiness.ps1`;
- document baseline refresh semantics in the source-layout policy.

## Exclusions

The report excludes generated/build/vendor/migration/fixture paths and minified browser JavaScript. It does not scan generated CSS or historical documents.

## Validation

The guarded operator:

- creates the baseline only in a detached worktree first;
- executes the PowerShell report in baseline-sync mode;
- independently recomputes the expected review-triggered snapshot in Node and requires exact parity with the generated JSON;
- verifies release-readiness integration and policy markers;
- runs `git diff --check`;
- applies the exact validated baseline snapshot to the real checkout only after sandbox GREEN.

No deployment is performed.

# 01-212 - manual runner source-contract sync

## Runtime trigger

The operator ran `apply-01-212-admin-manual-backup-v1_1.ps1`. The implementation files were applied successfully and PowerShell/JavaScript syntax gates passed, but targeted Maven stopped in `ProductionBackupContourSourceContractTest.helpersSupportCriticalFullCustomAndSelectiveRestore`.

The failing source-contract still expected flattened PowerShell command text:

- `-Action backup -Mode critical`
- `-Action full -Mode full`

The actual host runner now invokes the helper with a PowerShell argument array:

- `"-Action", "backup", "-Mode", "critical"`
- `"-Action", "full", "-Mode", "full"`

This is a test/runtime representation mismatch, not a backup-runtime failure.

## Repair

- keep the already-applied manual backup UI/API/queue/runner implementation unchanged;
- update only the two stale PowerShell scheduled-runner assertions to the current argument-array contract;
- verify manual-queue source markers remain present;
- rerun Maven `test-compile` and targeted backup tests;
- run `git diff --check`.

No git add/commit/push/reset/checkout/clean is performed.

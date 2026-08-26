# 01-212 backup smoke Windows PowerShell parser repair

## Triggering user evidence

The user ran finalize-01-212-smoke-source-lifecycle-v3.ps1. The helper rewrote scripts/docker-production-backup-smoke.ps1 and then its own parser gate failed with an invalid variable-reference error: a colon followed an unbraced variable name.

## Root cause

The generated Invoke-ComposeChecked error message contained `$code:` inside an interpolated string. Windows PowerShell parses the colon as part of the variable reference. The valid form is `${code}:`.

## Repair

- replace the exact invalid interpolation with `${code}:`;
- reject any remaining unsafe unbraced `$name:` pattern in the generated smoke script;
- re-parse all production PowerShell helpers;
- verify shell scripts, Compose, Maven targeted tests and full backup/restore smoke.

No stage/commit/push/reset/checkout/clean is performed.
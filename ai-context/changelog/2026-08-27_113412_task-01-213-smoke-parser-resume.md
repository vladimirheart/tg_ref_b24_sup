# 01-213 - resume after PowerShell 5.1 smoke parser failure

## Runtime trigger

The v1.1 apply successfully wrote the 01-213 implementation and then stopped during verification:

`PowerShell parser failed for scripts/docker-alertmanager-delivery-smoke.ps1: Недопустимая ссылка на переменную. За знаком : не следует допустимый знак имени переменной.`

The failing generated line was:

`throw "docker compose failed with exit code $LASTEXITCODE: ..."`

## Root cause

In Windows PowerShell 5.1, `$LASTEXITCODE:` inside a double-quoted string is parsed as a scoped-variable form. The variable must be delimited:

`${LASTEXITCODE}:`

The problem is limited to the generated E2E smoke script. The v1.1 log shows that all implementation writes completed before the parser gate.

## Resume strategy

- do not rerun the original apply;
- fail closed unless the smoke file has the exact generated bad SHA or exact corrected SHA;
- rewrite only `scripts/docker-alertmanager-delivery-smoke.ps1`;
- verify all important 01-213 implementation markers remain present;
- rerun PowerShell parser checks;
- run Bash syntax checks when available;
- validate Docker Compose config only; do not start the project;
- run Maven test-compile and targeted 01-213 / backup source-contract tests;
- run `git diff --check`.

No git add/commit/push/reset/checkout/clean is performed.

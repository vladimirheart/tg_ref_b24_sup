# 01-211 Phase E1 — Compose interpolation and Windows Bash recovery

Date: 2026-08-26 11:30 +03:00
Task: 01-211

## User input

Пользователь прислал полный terminal output запуска `apply-01-211-phase-e-compose-role-split.ps1`.

Ключевые строки входного вывода:

```text
OK: spring-panel test-compile
OK: 01-211 Phase E targeted tests
OK: base docker compose config
level=warning msg="The \"template_dir\" variable is not set. Defaulting to a blank string."
OK: edge docker compose config
WSL ... execvpe(/bin/bash) failed: No such file or directory
bash -n failed for docker-production-up.sh
```

## Diagnosis

Два независимых факта:

1. `.sh` syntax check был запущен через Windows WSL `bash.exe` shim, но в WSL нет `/bin/bash`. Это host-tooling limitation и не должно делать Phase E красным после успешных PowerShell/Maven/Compose checks.
2. Compose warning про `template_dir` является реальной topology bug: `$template_dir` внутри Compose `command` интерполировался Compose на host и превращался в пустую строку до запуска nginx.

## Changes

- Escaped nginx shell variable as `$$template_dir` in `docker-compose.production-edge.yml`.
- Extended topology source-contract test so unescaped template variable cannot return silently.
- Recovery verification treats an installed-but-non-runnable Bash shim as a warning.
- Re-runs Phase E targeted tests, base/edge Compose config and `git diff --check`.

## Status

Phase E remains `🟡` until the isolated real Docker role/scale smoke passes.

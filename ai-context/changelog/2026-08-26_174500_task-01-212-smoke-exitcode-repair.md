# 01-212 — repair smoke runtime false-failure on docker compose output

## Промпт пользователя

- `у меня валится скрипт C:\Users\SinicinVV\git_h\tg_ref_b24_sup\repair-01-212-smoke-parser-and-run-v1.ps1. результат вывода таков:`

## Что произошло

Пользовательский `repair-01-212-smoke-parser-and-run-v1.ps1` проходил parser/static этапы и падал уже на `scripts/docker-production-backup-smoke.ps1` с сообщением вида `MinIO smoke seed sh -n failed with exit code #1 ...`.

## Корень проблемы

- проблема была не в `MinIO` shell-файле и не в `docker compose` overlay;
- `Invoke-ComposeStatus()` в `scripts/docker-production-backup-smoke.ps1` возвращал не только `$LASTEXITCODE`, но и весь stdout `docker compose`;
- дальше `Invoke-ComposeChecked()` сравнивал массив строк build/runtime вывода с `0`, из-за чего успешный запуск трактовался как падение;
- параллельно smoke helper уже содержал parser fix для `${code}:`, поэтому теперь требовалось починить именно runtime semantics возврата кода.

## Что изменено

- в `scripts/docker-production-backup-smoke.ps1` вывод `docker compose` теперь уходит в `Out-Host`, а наружу из `Invoke-ComposeStatus()` возвращается только числовой exit code;
- MinIO smoke shell invocation оставлен в совместимом виде через `sh -c`, чтобы parser/runtime шаги на Windows PowerShell не зависели от неоднозначной передачи аргументов;
- повторный прогон `repair-01-212-smoke-parser-and-run-v1.ps1` автоматически дописал GREEN-evidence в `ai-context/tasks/task-details/01-212.md`.

## Проверка

- `powershell -NoProfile -ExecutionPolicy Bypass -File scripts\docker-production-backup-smoke.ps1 -KeepArtifacts`
- `powershell -NoProfile -ExecutionPolicy Bypass -File .\repair-01-212-smoke-parser-and-run-v1.ps1`

Оба сценария завершились успешно. `01-212` остаётся `YELLOW` только из-за ещё не закрытого реального off-host DR proof.

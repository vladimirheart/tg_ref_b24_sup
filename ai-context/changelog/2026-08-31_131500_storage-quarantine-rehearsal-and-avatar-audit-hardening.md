# 2026-08-31 - storage quarantine rehearsal and client avatar audit hardening

## User prompt

- "давай дальше по этой-же задаче. осталось немного уже."
- "я пытался сам частично добить одну проблему но вылезли проблемы"

## What changed

- Выполнен clean-worktree recovery-bound storage evidence cycle для `01-220` на `HEAD 5d1456f4dd87b6bd06cc9893b0c043979171abb8`.
- Собран новый evidence directory `C:\Users\SinicinVV\git_h\iguana-legacy-storage-inventory\20260831-122216` с fresh inventory, exact mapping, candidate manifest и reviewed manifest.
- Non-mutating `docker-production-storage-quarantine.ps1 -Apply -WhatIf` успешно завершён на reviewed manifest без перемещения legacy-файлов и без создания quarantine root.
- `scripts/docker-production-client-avatar-cutover-audit.ps1` расширен: теперь он проверяет не только `client_avatar_history`, но и реальные локальные client-avatar кандидаты из `attachments/avatars`.
- Зафиксировано, что canonical S3 содержит client-avatar объекты для найденных legacy local файлов `380742186*` и `409963501*`.
- Обновлены `01-220` и `01-221` реальными evidence paths, counters и verdict.

## Files changed

- `scripts/docker-production-client-avatar-cutover-audit.ps1`
- `ai-context/tasks/task-list.md`
- `ai-context/tasks/task-details/01-220.md`
- `ai-context/tasks/task-details/01-221.md`
- `ai-context/changelog/2026-08-31_131500_storage-quarantine-rehearsal-and-avatar-audit-hardening.md`

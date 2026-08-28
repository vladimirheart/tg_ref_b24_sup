# 2026-08-28 - provider-independent fix for storage quarantine `-Apply -WhatIf`

## Промпт пользователя

`проблема с выполнением скрипта`

## Контекст

По приложенному PowerShell-логу падал сценарий `scripts/docker-production-storage-quarantine.ps1` в фазе `manifest-source-verification` при запуске с `-Apply -WhatIf`.

До падения проходили:

- `-ValidateOnly` для quarantine helper;
- fresh inventory;
- full exact mapping;
- reviewed manifest validation.

Ошибка происходила на первом SHA-проверяемом source path и выглядела как provider-level `What if: Performing the operation "Retrieve the value for property 'ProviderPath'" ...`, после чего helper выбрасывал `Unable to read SHA-256 for manifest source`.

## Что изменено

- В `scripts/docker-production-storage-quarantine.ps1` добавлен `Get-FileLengthReadOnly`, читающий размер файла через `[IO.FileInfo]`.
- `Get-Sha256ReadOnly` переведён с `Get-FileHash` на `[IO.File]::Open(...)` + `[Security.Cryptography.SHA256]::Create()`.
- Верификация manifest source больше не использует `Get-Item -LiteralPath $source` и не зависит от PowerShell provider semantics под `-WhatIf`.
- В `spring-panel/src/test/java/com/example/panel/runtime/DockerProductionStorageLegacyPurgeSourceContractTest.java` обновлён строковый контракт:
  - ожидается provider-independent helper для длины и SHA;
  - убраны устаревшие ожидания literal `stage=storage-cutover-gate` / `stage=client-avatar-cutover-audit`;
  - вместо них фиксируются реальные вызовы `-Stage "storage-cutover-gate"` и `-Stage "client-avatar-cutover-audit"`.
- В `ai-context/tasks/task-details/01-217.md` зафиксированы новый hotfix, подтверждающая проверка и актуальный следующий шаг.

## Проверка

- `spring-panel\mvnw.cmd "-Dtest=DockerProductionStorageLegacyPurgeSourceContractTest" test` -> `BUILD SUCCESS`.
- На реальном reviewed-manifest path `...8ba36c4f-dc72-443e-8561-25de40d1e43f_изображение.png`:
  - `Get-FileHash -LiteralPath ...` при `$WhatIfPreference=$true` по-прежнему печатает provider `What if` и возвращает пустой `legacy_hash=`;
  - .NET SHA-путь на том же файле возвращает `d4f939ac87bc9cf868d843e4274f6f7a47adfa15fd438c98893e3e75c9f9de55`.
- Полный запуск `docker-production-storage-quarantine.ps1 -Apply -WhatIf` из текущего репозитория после правки упирается уже в штатный guard `Production working tree must be clean.`, то есть старая ошибка с чтением SHA больше не является первым блокером.

## Что дальше

- Выполнить реальный `-Apply -WhatIf` из чистого дерева после коммита/fast-forward этого hotfix.
- Сгенерировать fresh inventory и reviewed manifest заново на новом HEAD, потому что evidence commit-bound.

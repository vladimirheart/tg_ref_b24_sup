# 01-212 — sync portable runtime source-contract with dynamic backup policy runner

## Промпт пользователя

- `пытаюсь выполнить скрипт C:\Users\SinicinVV\git_h\tg_ref_b24_sup\resume-01-212-portable-recovery-runtime-v1_2.ps1 но ловлю ошибки`

## Симптом

`resume-01-212-portable-recovery-runtime-v1_2.ps1` проходил PowerShell parser, partial-state checks и Compose validation, но падал на Maven gate:

- `ProductionBackupContourSourceContractTest.helpersSupportCriticalFullCustomAndSelectiveRestore`
- ожидал literal-маркеры `IGUANA_BACKUP_CRITICAL_ENABLED` и `IGUANA_BACKUP_FULL_ENABLED`
- фактический runtime уже использовал динамический lookup `Get-Env "IGUANA_BACKUP_${Prefix}_ENABLED"`

## Причина

Source-contract тест отстал от актуального portable runtime. `scripts/run-backup-policy.ps1` намеренно перешёл на общий `Prefix`-driven scheduler contract для `CRITICAL` и `FULL`, а тест всё ещё проверял старую жёстко зашитую форму env-ключей.

## Что изменено

- обновлён `spring-panel/src/test/java/com/example/panel/runtime/ProductionBackupContourSourceContractTest.java`
- вместо устаревших literal env-ключей тест теперь проверяет:
  - `Get-Env "IGUANA_BACKUP_${Prefix}_ENABLED"`
  - `Get-Env "IGUANA_BACKUP_${Prefix}_FREQUENCY"`
  - `Get-Env "IGUANA_BACKUP_${Prefix}_TIME"`
  - `Is-Due "CRITICAL"`
  - `Is-Due "FULL"`
  - запусков `-Action backup -Mode critical` и `-Action full -Mode full`

## Проверка

- `spring-panel\mvnw.cmd -q -Dtest=ProductionBackupContourSourceContractTest test`
- `powershell -NoProfile -ExecutionPolicy Bypass -File .\resume-01-212-portable-recovery-runtime-v1_2.ps1`

Оба сценария завершились успешно. `01-212` по-прежнему остаётся `YELLOW` только до реального external failure-domain proof и ручной операторской проверки.

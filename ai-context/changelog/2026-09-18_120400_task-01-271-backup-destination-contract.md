# 01-271 — Backup destination model/UI contract — phase A

## Время

2026-09-18 12:04 +03:00

## Пользовательский промт

- «прочти handoff и продолжаем»;
- из handoff: ранее согласовано «норм, делаем» для полноценной настройки external backup storage через admin UI.

## Что сделано

- создан task `01-271` вместо конфликтующего `01-261`: changelog фаз `01-260` уже использует номера `01-261..01-270`;
- добавлена нормализованная модель destination типов `local-filesystem`, `smb-unc`, `mounted-network-filesystem`;
- сохранена legacy-совместимость через `IGUANA_BACKUP_DESTINATION_DIR`;
- SMB destination раскладывается на server/share/subpath;
- auth metadata хранит только mode/username/credential_ref, inline secret fields backend отклоняет;
- Local destination автоматически классифицируется как `not_dr`;
- зафиксирован read-only host probe contract: steps и stable normalized error codes;
- Backup & recovery UI разделён на destination card и retention, добавлены type/auth/credential-ref поля и DR/probe status;
- host-side probe execution намеренно недоступен в phase A и остаётся для phase B;
- добавлены unit/source-contract tests и runbook note.

## Затронутые области

- `ai-context/tasks/task-list.md`
- `ai-context/tasks/task-details/01-271.md`
- `spring-panel/src/main/java/com/example/panel/service/BackupDestinationSettings.java`
- `spring-panel/src/main/java/com/example/panel/service/BackupSettingsService.java`
- `spring-panel/src/main/resources/templates/settings/fragments/backup-recovery.html`
- `spring-panel/src/main/resources/static/js/settings-backup-runtime.js`
- `spring-panel/src/test/java/com/example/panel/service/BackupDestinationSettingsTest.java`
- `spring-panel/src/test/java/com/example/panel/runtime/ProductionBackupContourSourceContractTest.java`
- `docs/runbooks/production-backup-recovery.md`

## Не выполнялось

- host/network probe;
- SMB authentication;
- write/delete probe;
- backup/restore;
- deploy/restart;
- credential mutation;
- DB migration/backfill.

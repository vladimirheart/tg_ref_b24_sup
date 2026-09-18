# 01-271 — Backup & recovery UI polish pre-deploy hotfix

## Контекст

Remote review после push commit `3a34864e6d11341fd2ced49f94f1a3f477beb151` обнаружил два дублирующихся section heading в `backup-recovery.html`: compact heading и оставшийся legacy plain `h6` для Retention и Периодичность. Production deploy этого commit был остановлен до исправления.

## Что меняется

- удаляются только два legacy duplicate heading;
- compact `backup-section-title` остаются единственными заголовками соответствующих секций;
- source-contract получает regression assertions, запрещающие возврат plain `<h6>Retention</h6>` и `<h6>Периодичность</h6>`;
- backend, JS, SCSS, probe/auth/backup semantics не меняются.

## Safety

Validate-only оператор работает в sandbox clone, не меняет production/runtime, не запускает probe, SMB auth, backup/restore, deploy/restart и не меняет credentials.

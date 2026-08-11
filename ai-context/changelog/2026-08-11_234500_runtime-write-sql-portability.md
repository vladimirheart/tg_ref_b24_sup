# 2026-08-11 23:45:00 - runtime write sql portability

## Request
- `давай следующий крупный шаг.`
- `что осталось по задаче?`

## Summary
- в active write-path сервисах `spring-panel` убраны SQLite-specific `datetime('now'...)`, которые мешали external PostgreSQL runtime;
- `DialogTicketLifecycleService` теперь формирует `created_at` и `expires_at` для `pending_feedback_requests` на стороне Java и больше не зависит от SQLite-only date arithmetic;
- `SettingsItEquipmentService`, `SettingsParameterService` и NetBox sync-path переведены на portable `CURRENT_TIMESTAMP`;
- карточка `01-181` обновлена новым фактическим остатком работ по runtime SQL portability.

## Files Changed
- `spring-panel/src/main/java/com/example/panel/service/DialogTicketLifecycleService.java`
- `spring-panel/src/main/java/com/example/panel/service/SettingsItEquipmentService.java`
- `spring-panel/src/main/java/com/example/panel/service/SettingsParameterService.java`
- `spring-panel/src/main/java/com/example/panel/service/NetBoxObjectPassportSyncService.java`
- `spring-panel/src/test/java/com/example/panel/service/NetBoxObjectPassportSyncServiceTest.java`
- `spring-panel/src/test/java/com/example/panel/service/SettingsParameterServiceTest.java`
- `ai-context/tasks/task-details/01-181.md`

## Verification
- `git diff --check`
- `cmd /c mvnw.cmd -q -DskipTests compile` (`spring-panel`) - passed
- targeted `spring-panel` tests were not run because repository still has a pre-existing unrelated `testCompile` blocker in `src/test/java/com/example/panel/service/BotProcessServiceTest.java:364`

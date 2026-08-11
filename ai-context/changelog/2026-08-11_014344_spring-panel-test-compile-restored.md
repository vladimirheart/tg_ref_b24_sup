# 2026-08-11 01:43:44 - spring panel test compile restored

## Request
- `давай дальше`

## Summary
- снят старый compile-blocker в `spring-panel/src/test/java/com/example/panel/service/BotProcessServiceTest.java`;
- обновлены тесты, отставшие от текущих сигнатур `ChatMessageDto` и `RmsLicenseMonitoringService`;
- исправлен `ChatAttachmentMetadataAvailabilityServiceTest`, чтобы он использовал однозначный `RowMapper`-stub и компилировался вместе с остальным test-suite;
- `spring-panel` снова проходит `test-compile`, а targeted-запуски `BotProcessServiceTest` и `ChatAttachmentMetadataAvailabilityServiceTest` завершаются успешно;
- карточка `01-181` обновлена: verification-path вокруг PostgreSQL-first readiness снова рабочий.

## Files Changed
- `spring-panel/src/test/java/com/example/panel/service/BotProcessServiceTest.java`
- `spring-panel/src/test/java/com/example/panel/service/ChatAttachmentMetadataAvailabilityServiceTest.java`
- `spring-panel/src/test/java/com/example/panel/service/DialogDetailsReadServiceTest.java`
- `spring-panel/src/test/java/com/example/panel/service/DialogWorkspaceHistorySliceServiceTest.java`
- `spring-panel/src/test/java/com/example/panel/service/DialogWorkspaceParityServiceTest.java`
- `spring-panel/src/test/java/com/example/panel/service/DialogWorkspacePayloadAssemblerServiceTest.java`
- `spring-panel/src/test/java/com/example/panel/service/RmsLicenseMonitoringServiceTest.java`
- `ai-context/tasks/task-details/01-181.md`

## Verification
- `cmd /c mvnw.cmd -q test-compile` (`spring-panel`) - passed
- `cmd /c mvnw.cmd -q -Dtest=ChatAttachmentMetadataAvailabilityServiceTest test` (`spring-panel`) - passed
- `cmd /c mvnw.cmd -q -Dtest=BotProcessServiceTest test` (`spring-panel`) - passed
- `git diff --check` - only CRLF/LF warnings, no diff formatting errors

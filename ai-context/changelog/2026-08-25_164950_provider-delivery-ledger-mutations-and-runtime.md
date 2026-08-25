# 2026-08-25 16:49:50 - provider delivery ledger mutations and runtime coverage

## Initiated by

- `отлично. бери теперь в работу задачу 01-207`

## What changed

- Оформлена и выполнена задача `01-207`: создан `ai-context/tasks/task-details/01-207.md`, статус в `ai-context/tasks/task-list.md` переведён в `🟣`.
- `provider_delivery_ledger` расширен на panel-side mutation paths:
  - provider-side `edit`;
  - provider-side `delete`;
  - validation/transport failures для этих операций через общий `DialogReplyTransportResult`.
- `BotRuntimeTicketWriteService` теперь пишет в ledger trusted runtime-observed outbound success для:
  - `recordOperatorRelay(...)` как `operator_runtime/text`;
  - `markOperatorMessageEdited(...)` как `operator_runtime/text_edit`.
- Добавлен helper `ProviderDeliveryLedgerService.recordObservedSuccess(...)`, чтобы не плодить отдельный runtime-owned ledger contour.
- Обновлены целевые тесты:
  - `DialogReplyServiceTest`;
  - `BotRuntimeTicketWriteServiceTest`;
  - `DialogQuickActionsIntegrationTest`.
- Во время верификации дополнительно закрыты runtime/test compatibility issues, мешавшие прогону:
  - явный constructor DI для service-beans с test-only overload constructors;
  - lenient timestamp converter для `Notification.createdAt`;
  - SQLite-safe generated-key fallback в media path `DialogReplyTargetService`;
  - актуализированы media attachment expectations под `by-storage-key` URL.

## Verification

- Выполнен targeted прогон:
  - `./mvnw -q "-Dtest=DialogReplyServiceTest,BotRuntimeTicketWriteServiceTest" test`
  - `./mvnw -q "-Dtest=DialogQuickActionsIntegrationTest#quickActionsApiMediaReplyRefreshesDetailsWorkspaceAndAuditTrail+quickActionsApiMediaReplyNotifiesPeerParticipantsThroughNotificationApi" test`
- Перед адресным интеграционным прогоном выполнялась пересборка test classes:
  - `./mvnw -q -DskipTests test-compile`
- Полный `DialogQuickActionsIntegrationTest` был прогнан в ходе работы; после него были точечно исправлены только оставшиеся два media-scenario.

## Files

- `ai-context/tasks/task-list.md`
- `ai-context/tasks/task-details/01-207.md`
- `ai-context/changelog/2026-08-25_164950_provider-delivery-ledger-mutations-and-runtime.md`
- `spring-panel/src/main/java/com/example/panel/service/DialogReplyService.java`
- `spring-panel/src/main/java/com/example/panel/service/DialogReplyTransportService.java`
- `spring-panel/src/main/java/com/example/panel/service/ProviderDeliveryLedgerService.java`
- `spring-panel/src/main/java/com/example/panel/service/BotRuntimeTicketWriteService.java`
- `spring-panel/src/main/java/com/example/panel/service/DialogReplyTargetService.java`
- `spring-panel/src/main/java/com/example/panel/entity/Notification.java`
- `spring-panel/src/test/java/com/example/panel/service/DialogReplyServiceTest.java`
- `spring-panel/src/test/java/com/example/panel/service/BotRuntimeTicketWriteServiceTest.java`
- `spring-panel/src/test/java/com/example/panel/controller/DialogQuickActionsIntegrationTest.java`

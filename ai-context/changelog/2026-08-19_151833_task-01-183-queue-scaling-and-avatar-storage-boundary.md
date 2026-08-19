# task-01-183 - queue scaling and avatar storage boundary

Date: 2026-08-19 15:18:33
Task: 01-183
Initiated by: user request `давай теперь дожать scaling semantics на очередях и consumers/workers, чтобы multi-instance был не только у panel schedulers, но и по inbound/outbound processing; закрыть оставшиеся binary/storage debt участки вне нового attachment boundary, если хотим полностью уйти от local live storage;`

## Completed
- Hardened RabbitMQ listener scaling semantics in `spring-panel`:
  - added explicit inbound listener concurrency/prefetch settings for:
    - inbound client message consumer
    - conversation ticket created consumer
  - split listener container factories per flow instead of relying on one implicit default container.
- Hardened RabbitMQ listener scaling semantics in `java-bot`:
  - added explicit outbound listener concurrency/prefetch settings for feedback prompt consumer
  - dedicated listener container factory now controls bot-side outbound worker scaling.
- Added bot-side outbound delivery ledger:
  - new `OutboundTransportDeliveryLedgerService`
  - outbound feedback prompt dispatch is now idempotent by `eventId`
  - repeated deliveries/redeliveries are skipped once an event is already delivered
  - failed deliveries are tracked in ledger state instead of silently duplicating on retry.
- Hardened backend-side outbound feedback scheduling:
  - `FeedbackPromptDispatchSchedulerService` now runs under Redis lease via `RuntimeCoordinationService`
  - this removes the most visible multi-backend race for parallel feedback prompt publishing.
- Closed remaining avatar/user-photo binary storage debt on panel side:
  - extended `AttachmentObjectStorageService` with canonical `avatars` domain
  - `AttachmentService.downloadAvatar(...)` now serves avatars through object-storage boundary
  - `PanelUserPhotoService` now owns avatar upload/store semantics on top of object storage
  - `AuthManagementApiController` no longer writes user photos directly to local disk
  - `ClientProfileApiController` no longer writes Telegram profile avatars directly to local disk
  - `AvatarService` now loads client avatars through canonical avatar storage instead of local-filesystem-only path logic.
- Synced bot schema assets with new outbound transport ledger table:
  - `schema-postgres.sql`
  - `schema-sqlite.sql`
  - `schema.sql`

## Verification
- `spring-panel`: `./mvnw.cmd -q -DskipTests compile`
- `spring-panel`: `./mvnw.cmd -q "-Dtest=FeedbackPromptDispatchSchedulerServiceTest,AuthManagementApiControllerWebMvcTest,DialogLookupReadServiceTest,PostgresRuntimeReadinessVerifierTest,ChatAttachmentMetadataAvailabilityServiceTest,ObjectPassportPhotoStorageServiceTest,BotRuntimeTicketWriteServiceTest" test`
- `java-bot`: `./mvnw.cmd -q -pl bot-core,bot-telegram,bot-vk -am -DskipTests compile`
- `java-bot`: `./mvnw.cmd -q -pl bot-core "-Dtest=OutboundFeedbackPromptDispatchServiceTest" test`

## Notes
- Multi-instance semantics are now materially stronger not only for panel schedulers, but also for Rabbit consumers and outbound worker delivery behavior.
- Avatar/user-photo storage is no longer a direct local-disk live contour in active panel runtime paths; it now follows the same canonical object-storage boundary direction as other attachment domains.
- Remaining broader scope after this package is higher-level infra/business hardening, not the earlier obvious queue/avatar contour debt.

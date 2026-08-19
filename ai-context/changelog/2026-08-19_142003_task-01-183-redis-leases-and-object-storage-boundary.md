# task-01-183 - redis leases and object storage boundary

Date: 2026-08-19 14:20:03
Task: 01-183
Initiated by: user request `забирай в работу: Redis/leases/live coordination и multi-instance worker coordination. MinIO/S3 как обязательный attachment boundary вместо local-disk-first модели.`

## Completed
- Added canonical runtime coordination layer in `spring-panel`:
  - new `RuntimeCoordinationProperties`
  - new `RuntimeCoordinationService`
  - PostgreSQL readiness verifier now requires Redis coordination readiness in PostgreSQL contour
  - scheduled/live singleton jobs now run under Redis leases:
    - `UiEventOutboxWatcher`
    - `SlaEscalationWebhookNotifier`
    - `WorkspaceGuardrailWebhookNotifier`
    - `SslCertificateMonitoringScheduler`
    - `RmsLicenseMonitoringScheduler`
- Added canonical object-storage boundary in `spring-panel`:
  - new `ObjectStorageProperties`
  - new `AttachmentObjectStorageService`
  - dialog attachments, knowledge-base files, and object passport photos now go through object-storage abstraction instead of direct local-disk-first handling
  - PostgreSQL readiness verifier now requires object-storage readiness in PostgreSQL contour
- Added canonical object-storage runtime contract in `java-bot`:
  - new bot-side `ObjectStorageProperties`
  - bot attachment storage now supports `local_fs` compatibility mode and `s3` canonical mode
  - attachment storage returns storage key/provider contract instead of raw filesystem `Path`
  - Telegram/VK transport paths now persist and relay attachment storage keys, materializing temp files only at relay edge when required by Telegram API
- Normalized attachment metadata provider semantics:
  - bot-side metadata now marks non-external attachments as `s3` when object storage mode is enabled
  - panel-side metadata creation and availability reconciliation now preserve/resolve canonical storage provider instead of forcing `local_fs`
- Added runtime configuration surface in `application.yml` for:
  - `app.coordination.*`
  - `app.storage.object.*`
  - `app.storage.passport-photos`
- Added AWS S3 SDK dependency management to both `spring-panel` and `java-bot`.

## Verification
- `spring-panel`: `./mvnw.cmd -q -DskipTests compile`
- `spring-panel`: `./mvnw.cmd -q "-Dtest=PostgresRuntimeReadinessVerifierTest,ChatAttachmentMetadataAvailabilityServiceTest,ObjectPassportPhotoStorageServiceTest,BotRuntimeTicketWriteServiceTest,DialogWorkspacePayloadAssemblerServiceTest,ObjectPassportApiControllerWebMvcTest,IncidentServiceTest,IncidentApiControllerWebMvcTest" test`
- `java-bot`: `./mvnw.cmd -q -pl bot-core,bot-telegram,bot-vk -am -DskipTests compile`

## Notes
- Production contour is now prepared for Redis-backed lease coordination and S3/MinIO-backed attachment storage, while preserving `local_fs` compatibility for non-production/dev modes.
- Remaining broader infra scope is now above this layer: queue/consumer scaling semantics, incident workflow depth, and final non-canonical binary/storage debts outside the newly unified attachment boundary.

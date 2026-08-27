# 01-217 / 01-218 - hardening legacy media cutover and final cutover planning

## Промпт пользователя

`давай добивать переезд с legacy`

## Что изменено

- В `spring-panel/src/main/java/com/example/panel/service/ChatAttachmentMetadataAvailabilityService.java` убран вызов несуществующего helper-а `DialogDataAccessSupport.loadTableColumns(...)`; вместо этого добавлен безопасный двухшаговый backfill missing metadata rows из `chat_history` с fallback на минимальный SQL для старых схем.
- В `spring-panel/src/main/java/com/example/panel/storage/AttachmentService.java` legacy `download/describe by-path` теперь сначала пытаются извлечь `attachments/...` suffix и открыть файл через object storage, а не только через local filesystem.
- В `spring-panel/src/main/java/com/example/panel/storage/ObjectStorageProperties.java`, `spring-panel/src/main/java/com/example/panel/storage/AttachmentObjectStorageService.java` и `spring-panel/src/main/java/com/example/panel/service/PanelUserPhotoService.java` закреплён явный cutover-флаг `legacyLocalFallbackEnabled`, чтобы final switch на отказ от local legacy storage можно было выполнить управляемо.
- В `spring-panel/src/test/java/com/example/panel/service/ChatAttachmentMetadataAvailabilityServiceTest.java`, `spring-panel/src/test/java/com/example/panel/storage/AttachmentObjectStorageServiceLegacyFallbackTest.java` и `spring-panel/src/test/java/com/example/panel/storage/AttachmentServiceMediaResponseTest.java` добавлены проверки для нового metadata backfill, legacy-path object-storage открытия и поведения при выключенном fallback.
- В `scripts/docker-production-storage-backfill.ps1` backfill расширен на реальные legacy roots `java-bot/attachments`, `spring-panel/attachments/avatars` и historical avatar directories; проверка наличия dialog attachments теперь идёт по нескольким legacy-каталогам.
- В `docker-compose.production-contour.yml` добавлена явная env-настройка `APP_STORAGE_OBJECT_LEGACY_LOCAL_FALLBACK_ENABLED` с безопасным значением по умолчанию `true`, чтобы финальный switch выполнялся отдельно и осознанно.
- В `ai-context/tasks/task-list.md` и `ai-context/tasks/task-details/01-218.md` заведён отдельный контур на финальный cutover: перевыкладку runtime, отключение fallback и подготовку purge legacy storage.

## Проверка

- `spring-panel\\mvnw.cmd "-Dtest=ChatAttachmentMetadataAvailabilityServiceTest,AttachmentObjectStorageServiceLegacyFallbackTest,AttachmentServiceMediaResponseTest" test` -> `BUILD SUCCESS`
- `powershell -ExecutionPolicy Bypass -File .\\scripts\\docker-production-storage-backfill.ps1` -> успешно, подтверждены `22` строки `chat_attachment_metadata` со статусом `available`
- `docker exec tg_ref_b24_sup-postgres-1 psql -U iguana -d iguana -Atc "SELECT COUNT(*) ..."` -> на 27 августа 2026 года остаётся `51` строка `chat_history` без `chat_attachment_metadata`; это и есть основной хвост для следующего deploy/cutover шага

## Остаточный контур

- Нужна новая сборка и перевыкладка `iguana-panel:local`, иначе automatic metadata backfill останется только в рабочем дереве, а не в живых контейнерах.
- Только после этого можно безопасно переводить `APP_STORAGE_OBJECT_LEGACY_LOCAL_FALLBACK_ENABLED=false` и готовить purge-runbook локального legacy storage.

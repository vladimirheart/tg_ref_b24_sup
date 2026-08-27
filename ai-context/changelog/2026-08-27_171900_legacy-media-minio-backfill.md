# 01-211 / 01-217 - legacy media backfill into MinIO and metadata recovery

## Промпт пользователя

`продолжи`

`и ты пишешь, что в minio только аватары пользователей панели, а остально это легаси. от легаси нужно избавляться и переносить в новый контур`

## Что изменено

- В `spring-panel/src/main/java/com/example/panel/storage/AttachmentObjectStorageService.java` добавлен явный `backfillDialogAttachmentByStorageKey(...)` и защита от лишнего повторного upload-а, если объект уже присутствует в MinIO/S3.
- В `spring-panel/src/main/java/com/example/panel/service/ChatAttachmentMetadataAvailabilityService.java` reconcile attachment metadata переведён на обновление по строковому `id`, а перед вычислением `availability_status` теперь выполняется попытка backfill-а по `storage_key`.
- В `spring-panel/src/test/java/com/example/panel/storage/AttachmentObjectStorageServiceLegacyFallbackTest.java` и `spring-panel/src/test/java/com/example/panel/service/ChatAttachmentMetadataAvailabilityServiceTest.java` добавлены/обновлены проверки под новый storage/backfill contract.
- Добавлен повторяемый migration script `scripts/docker-production-storage-backfill.ps1` для зеркалирования legacy media из `attachments/**` в MinIO и синхронизации `chat_attachment_metadata` в PostgreSQL.
- В `ai-context/tasks/task-list.md` и `ai-context/tasks/task-details/01-217.md` заведена отдельная задача на полный вывод legacy media из локального storage-контура.

## Выполнено на живом контуре

- Подняты обратно `postgres` и `rabbitmq`, после чего runtime перестал жить на таймаутах по недоступным backend dependency.
- Выполнен `scripts/docker-production-storage-backfill.ps1 -RestartRuntime`.
- В PostgreSQL переведены `22` строки `chat_attachment_metadata` из `missing` в `available`.
- После рестарта `panel-web` и `ops-worker` контур снова вышел в readiness, а `/login` начал отвечать `HTTP/1.1 200`.

## Проверка

- `./mvnw.cmd -q "-Dtest=AttachmentObjectStorageServiceLegacyFallbackTest,ChatAttachmentMetadataAvailabilityServiceTest" test`
- `powershell -NoLogo -NoProfile -ExecutionPolicy Bypass -File scripts/docker-production-storage-backfill.ps1 -RestartRuntime`
- `SELECT availability_status, count(*) FROM chat_attachment_metadata GROUP BY availability_status` -> `available = 22`
- `curl -I http://127.0.0.1:8080/login` -> `HTTP/1.1 200`

# 2026-08-03 19:00:00 - netbox photo import hardening

- Затронутые области:
  - `spring-panel/src/main/java/com/example/panel/storage/ObjectPassportPhotoStorageService.java`
  - `spring-panel/src/main/java/com/example/panel/service/NetBoxObjectPassportSyncService.java`
  - `ai-context/tasks/task-list.md`
  - `ai-context/tasks/task-details/01-178.md`
- Промпты пользователя:
  - `... начинает работать, но встречаю ошибку ... Поддерживаются изображения PNG, JPG, GIF, BMP или WebP`
- Что сделано:
  - импорт фото NetBox усилен поддержкой `.jfif` и распознаванием формата по сигнатуре файла при `application/octet-stream` и именах без расширения;
  - sync паспортов больше не падает на единичном неподдерживаемом image-attachment: такие файлы логируются как warning и пропускаются;
  - причина расследована на реальных NetBox image-attachments: в системе есть вложения без расширения и с `octet-stream`, хотя по содержимому это JPEG.

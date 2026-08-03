# 2026-08-03 20:15:00 - netbox site-level sync resilience

- Затронутые области:
  - `spring-panel/src/main/java/com/example/panel/service/NetBoxObjectPassportSyncService.java`
  - `ai-context/tasks/task-list.md`
  - `ai-context/tasks/task-details/01-179.md`
- Промпты пользователя:
  - `допустим. а почему упал синхрон а не продолжился далее, как это планировалось?`
- Что сделано:
  - sync NetBox переведён в режим частичной деградации по отдельному `site`: локальная ошибка объекта больше не обязана валить весь прогон;
  - ошибки `image-attachments` теперь могут переводиться в warning с импортом объекта без фото;
  - warnings по проблемным объектам собираются в итоговый статус sync, чтобы причина была видна не только в stack trace.

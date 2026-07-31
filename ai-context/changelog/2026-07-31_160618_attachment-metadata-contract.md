# 2026-07-31 16:06:18 - attachment metadata contract

- Задача: `01-161`
- Области изменений:
  - `spring-panel`: canonical attachment metadata migration, runtime read/write integration, storage-key download path
  - `java-bot`: metadata persistence for new chat history attachments
  - `scripts`: storage inventory extended for metadata-first diagnostics
  - `ai-context/tasks`: task status lifecycle updated

## Пользовательский промпт

> бери в работу 01-161

## Что сделано

- Добавлена canonical таблица `chat_attachment_metadata` в baseline schema, bot schema и Flyway migration `V39` с backfill существующих `chat_history.attachment`.
- Добавлен panel-side metadata service и `AttachmentStorageKeyResolver`, который нормализует legacy absolute paths и filename-only references в `storage_key`.
- Обновлён `DialogConversationReadService`: сначала использует `chat_attachment_metadata.storage_key`, а legacy `chat_history.attachment` оставляет как compat fallback.
- Добавлен новый download path `/api/attachments/tickets/by-storage-key`, чтобы UI больше не зависел от исторических `attachments/` vs `java-bot/attachments/` ссылок.
- Новые client/operator attachment записи теперь сразу создают metadata-строку и больше не остаются только path-строкой.
- `scripts/report-iguana-storage.py` теперь умеет отдельно показывать metadata-layer и legacy rows без metadata.
- Обновлены и прогнаны связанные unit-тесты/smoke-сборки под новый media metadata contract.

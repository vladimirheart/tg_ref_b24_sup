## 2026-05-22 07:24:30 — dialog-read runtime continuity

- Добавлен `DialogReadIntegrationTest` для live `SpringBootTest + SQLite`
  contract вокруг `DialogReadController`.
- В integration-пакете закреплены `history`,
  `history/previous`, `participants` и `operators`, включая
  `replyPreview`, `originalMessage`, `editedAt`, `deletedAt`,
  `forwardedFrom`, `last_read_at` read receipt и users-directory
  projection.
- Тестовый fixture сделан schema-aware: optional колонки
  `chat_history/users` доводятся до нужного contract прямо в тесте, чтобы
  runtime smoke не зависел от вариаций локальной SQLite схемы.
- `DialogReadControllerWebMvcTest` расширен на `history` с `channelId`,
  `participants` и `operators`, чтобы transport regression net был
  синхронизирован с новым live read-пакетом.
- Audit/roadmap обновлены: следующий dialog-read focus смещён в
  `details/workspace` runtime continuity, а не в оставшиеся базовые
  transport gaps.

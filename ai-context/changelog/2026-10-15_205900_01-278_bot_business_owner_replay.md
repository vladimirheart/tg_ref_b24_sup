# 01-278 R109: явный владелец в bot transport и неизменность replay

- Время: 2026-10-15 20:59:00 MSK.
- Промт пользователя: «продолжи».
- Baseline/HEAD/origin_main: `487c09f0a504563f99f46f6efab0d98a0afe7881`.
- Файлы: bot-core `ConversationTicketCreationCommand`, `ConversationTicketCreatedEvent`,
  `ConversationTicketCreatedPublisher`, `IntegrationTransportOutboxService`,
  `TicketService` и их tests; spring-panel `ConversationTicketCreationIngestionService`,
  `IntegrationInboundEventInboxService` и их tests;
  `ai-context/tasks/task-details/01-278.md`.
- Состояние: `source_applied=true`, `staged=false`, `committed=false`,
  `pushed=false`, `deployment=false`; отдельная sandbox не запускалась,
  targeted tests в рабочем checkout GREEN.

Исходящее bot-событие переносит необязательный явный `businessId` из команды
через publisher и PostgreSQL outbox. Старые Java-конструкторы и JSON без поля
совместимы; legacy `business` не становится источником ownership. Прямой
JDBC-путь отклоняет переданный канонический ID, который не смог бы сохранить.

Повторный `event_id` с другим business/channel/user/ticket/kind/platform
теперь отклоняется в bot outbox и panel inbox без замены исходного payload.
Повтор того же canonical ticket с новым event ID в strict panel path сверяет
замороженный business/channel; совпадение идемпотентно и не зависит от текущей
привязки канала. При выключенном флаге legacy поведение сохранено.

Чистые targeted прогоны: bot-core 28/28, spring-panel 31/31 (PostgreSQL в
Testcontainers), все bot-модули `package -DskipTests` GREEN, `git diff --check`
GREEN. Текущие Telegram/VK/MAX handlers не выдают `businessId`; включение
strict write flag, S2B решения, общий канал, S4 вопросники и production rollout
остаются за границей пакета. Рабочая БД, feature flags, migration/backfill,
staging/commit/push/deployment не менялись; задача 🟡.

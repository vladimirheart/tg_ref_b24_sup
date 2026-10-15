# 01-278 R108: явный canonical владелец при входящем создании обращения

- Время: 2026-10-15 20:25:00 MSK.
- Промт пользователя: «продолжай».
- Baseline: `HEAD` и `origin/main` — `1354bbfb9e379f7ee52b62a70a03b6c431ad1437`.
- Файлы: `ConversationTicketCreatedEvent`, `ConversationTicketCreationIngestionService`,
  `BusinessTicketWriteScopeResolver`, `MultibusinessFeatureFlags`, unit/PostgreSQL tests,
  `ai-context/tasks/task-details/01-278.md`.

Добавлено необязательное каноническое `businessId` во входящий контракт с
совместимостью старого Java-конструктора и JSON без нового поля. Включённый
`ticket-write-requires-business` теперь требует foundation, явный ID и
единственную активную `SINGLE_BUSINESS`-привязку активного канала к активному
бизнесу. Только после проверки создаётся корневое сообщение и ticket с
`tickets.business_id`. Общий канал, архивный бизнес, чужой/пустой ID и другие
режимы разрешения fail-closed. Текст `business` не используется как доказательство
ownership. При выключенном флаге старый путь и nullable `business_id` сохранены.

Чистый targeted Maven `clean test`: 21/21 GREEN (1 PostgreSQL, 20 unit), включая
старый JSON/replay и транспортный ops. Рабочая БД, конфигурация флагов,
migration/backfill, deployment, staging/commit/push не менялись. Текущий producer
ещё не отправляет явный ID, поэтому флаг не готов к включению; S2B/product
decisions, общие каналы, изоляция вопросников S4 и полная S3–S6 остаются
PENDING, статус задачи 🟡.

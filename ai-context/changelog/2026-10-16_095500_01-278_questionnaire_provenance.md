# 01-278 R116: сохранить версию общего вопросника в обращении

Время: 2026-10-16 09:55 MSK.

Промт пользователя: «продолжи по задаче 01-278».

Baseline, HEAD и свежий origin/main:
`dea161f946aca5bac2b75abe990a664d6aac38af`.

Изменённые области:

- Bot `ConversationTicketCreationCommand`, `ConversationTicketCreatedEvent`,
  publisher, `TicketService`, outbox и Telegram/VK/MAX handlers: перенос
  `questionTemplateId`/`templateSha256` из сохранённой сессии; совместимость
  старых конструкторов/JSON; отказ прямой JDBC-записи непредставимого scope.
- Panel event/inbox и `ConversationTicketCreationIngestionService`: проверка
  текущей verified версии до нового INSERT и сохранённой версии при replay.
  Outbox/inbox запрещают подмену либо потерю версии у повторного event ID.
- `Ticket` и additive V58: nullable парный ID/SHA-256 snapshot, форматные
  ограничения и immutable trigger. Старые записи не получают версию
  автоматически; обычное изменение статуса нового обращения разрешено.
- Шесть тестовых классов bot/panel; S0-контракт и detail задачи с checkpoint R116.

Проверки в detached worktree от baseline:

- bot-core: 37/37 GREEN, включая PostgreSQL outbox и прямой JDBC guard;
- panel: 31/31 GREEN, включая PostgreSQL inbox, полную Flyway-цепочку V1–V58
  и upgrade V57 → V58 с историческими unresolved/general tickets;
- все bot-модули: `package -DskipTests` GREEN; `git diff --check` GREEN;
- первоначальный bot integration run завершился RED на таймауте холодной
  инициализации тестового PostgreSQL до SQL assertions. Повторный прогон на
  прогретом Docker GREEN; таймауты и проверки не ослаблялись. У существующего
  JDBC-теста обновлено устаревшее ожидание текста ошибки на фактический
  `canonical ticket scope`, добавлены отказы GENERAL и отдельной версии.

Docker Desktop локально запущен для тестов. Рабочая/production БД, флаги,
конфигурация, backfill, deployment, staging, commit и push не изменялись.
Сгенерированные сборкой CSS восстановлены в sandbox и в изменение не входят.

Ограничение: новая незаконченная сессия со старой версией после rebind
отклоняется и требует нового старта; graceful resume/version registry,
отдельная очередь и права GENERAL, shared-channel выбор «Без бизнеса»,
provider E2E, S2B и полная S3–S6 остаются PENDING. Статус задачи 🟡,
включение flags и rollout не разрешены этим source-only этапом.

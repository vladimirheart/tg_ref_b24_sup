# 01-278: S2A — защита от коллизий ticket_id при создании обращения

- Дата: 2026-10-10.
- Исходный запрос пользователя: «работа по проекту; прочти handoff, проанализируй текущее состояние репо и продолжи». Продолжение после R1 local checkpoint GREEN.
- Базовый Git commit: `b486283950dc68ab6151fb089a797efa26913556`.
- Изменения: защита ingestion по составному ключу (user_id, ticket_id), явный fail-closed при уже занятом legacy ticket_id другого пользователя, тесты и обновление задачи.
- Проверка: guarded detached-worktree validate и целевой `ConversationTicketCreationIngestionServiceTest`; итог теста подтверждается логом runner до разрешения apply.
- Границы: source-only; без назначения business_id, legacy alias mapping, миграций, DB/backfill, flags, runtime, Docker и production rollout.
- S2B approval gate остаётся открытым; текущий шаг не обходит его.

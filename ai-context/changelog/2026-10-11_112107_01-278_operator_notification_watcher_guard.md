# 01-278: fail-closed OperatorNotificationWatcher

- Время: `2026-10-11 11:21:07`
- Файлы: `OperatorNotificationWatcher`, `OperatorNotificationWatcherTest`, `ai-context/tasks/task-details/01-278.md`
- Промпт пользователя: `продолжи. обрати внимание что появились новые изменения в коде проекта в рамках задачи`
- Что сделано: background watcher проверяет unique canonical ticket до AI, уведомлений, SLA escalation, audit и SSE side effects. Duplicate или отсутствующий legacy ID безопасно пропускается.
- Проверка: `11/11` targeted tests GREEN, включая PostgreSQL Testcontainers watcher fixture и runtime lifecycle source contract.
- Учтённые изменения: R12 task provenance docs остаются отдельным `PROPOSED_NOT_APPROVED` design-only контуром и не изменялись.
- Границы: canonical `business_id`, legacy aliases, Flyway-схема, рабочая БД, migration/backfill, feature flags и production rollout не менялись.

# 01-278 R106: метаданные завершения scoped-обращения

- Время: 2026-10-15 17:45:00 MSK.
- Промт пользователя: «дальше, небольшим пакетом, чтобы поместиться в лимит токенов».
- Baseline: `HEAD` и `origin/main` — `2fa605f5fa4d41a2851f8a810789f053046b6cf1`.
- Файлы: `BusinessScopedTicketReadService`, `business-dialogs.js`,
  `businesses/dialogs.html`, PostgreSQL/WebMvc/Node tests,
  `ai-context/tasks/task-details/01-278.md`.

В каноническое scoped-чтение добавлены `resolvedAt` и `resolvedBy` из
существующих полей `tickets`. Для `open`/`pending` оба поля скрыты даже при
устаревших значениях в строке; имя исполнителя ограничено 200 символами.
Карточка показывает время и исполнителя завершения через `textContent`, а при
смене контекста очищает эти поля. Новых миграций и бизнес-назначений нет.

Чистый targeted Maven `clean test`: 21/21 GREEN (11 PostgreSQL, 10 WebMvc);
20 Node UI-регрессий, `node --check` и `git diff --check` GREEN. Первый чистый
прогон был RED: сокращённая PostgreSQL test fixture не содержала существующих
в V1 полей `resolved_at`/`resolved_by`. Fixture дополнен, повторный чистый
прогон прошёл. Рабочая БД, feature flags, migration/backfill, deployment,
staging/commit/push не менялись. S2B/product decisions, изоляция вопросников
S4 и полная S3–S6 остаются PENDING; статус задачи 🟡.

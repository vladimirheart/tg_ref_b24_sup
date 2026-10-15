# 01-278 R95: сводка обращений по доступным бизнесам

- Время: 2026-10-15 11:33:57 MSK.
- Промт пользователя: «дальше».
- Файлы: `BusinessScopedTicketReadService`, `BusinessScopeReadController`,
  `business-dialogs.js`, `businesses/dialogs.html`, PostgreSQL и WebMvc tests,
  `ai-context/tasks/task-details/01-278.md`.

Добавлена сводка обращений по активным доступным бизнесам с общим итогом,
известными статусами и категорией `other`. Пустой бизнес виден с нулём,
обращение без canonical owner не засчитывается. Текущие grant и статус бизнеса
проверяются в SQL агрегации. Экран показывает сводку независимо от фильтров
списка и не принимает запоздалый ответ после смены контекста.

Чистый targeted Maven `clean test`: 13/13 GREEN (6 PostgreSQL, 7 WebMvc),
`node --check`, `git diff --check` GREEN. Промежуточные прогоны обнаружили
опечатку SQL text block и неверное ожидание HTTP-статуса в новом тесте;
исправлены до зелёного прогона. Рабочая БД, миграции/backfill, feature flags,
deployment, staging, commit/push не выполнялись. Задача остаётся 🟡,
решения S2B/product PENDING.

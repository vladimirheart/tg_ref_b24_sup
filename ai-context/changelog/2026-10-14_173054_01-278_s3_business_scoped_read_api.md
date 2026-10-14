# 01-278 R85: серверный список обращений по каноническому бизнесу

- Время: 2026-10-14 17:30:54 MSK.
- Промт: «продолжай - долго топчемся на на реализации задачи, хотя и важные пункты проходят».
- Области: `BusinessScopeReadController`, `BusinessScopedTicketReadService`,
  PostgreSQL integration test, `ai-context/tasks/task-details/01-278.md`.

Реализован первый прикладной S3 read API: каталог разрешённых активных
бизнесов и список обращений для одного бизнеса либо объединения доступных.
Доступ пересекает page authority, текущего пользователя и business grant;
SQL повторно проверяет active membership/grant в момент SELECT. Источник
владельца — только `tickets.business_id`, пустой owner не показывается.

В отдельном worktree на PostgreSQL 16 Testcontainers прошли 6 targeted
тестов (2 новых, 4 существующих), native Maven exit 0, 0 failures/errors.
Маршруты gated выключенным по умолчанию `multibusiness.foundation.enabled`.
Legacy endpoints/UI, существующие ticket owners, рабочая БД, миграция,
backfill, runtime flags и deployment не менялись. Задача остаётся 🟡;
S2B approvals и полный S3–S6 остаются отдельной работой.

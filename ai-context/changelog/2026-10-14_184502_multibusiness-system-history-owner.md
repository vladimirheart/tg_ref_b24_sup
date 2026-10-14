# 01-278 R88: подтверждённая связь системной истории

- Время: 2026-10-14 18:45:02 MSK.
- Промт пользователя: «дальше».
- Области: `BotRuntimeTicketWriteService`,
  `DialogAutoCloseSchedulerService`, `DialogNotificationService`,
  их PostgreSQL/unit-тесты, `ai-context/tasks/task-details/01-278.md`.

Новые системные события повторного открытия, автозакрытия и ticket-уведомления
получают составного владельца истории, когда он доказан текущим ticket.
Notification и bot-runtime INSERT повторно проверяют точный tuple и
уникальность raw ID. Исторический mismatch, отсутствующий parent либо
поздняя коллизия оставляют owner пустым, не раскрывая такую запись в новом
business-scoped API; прежняя доставка не перенаправляется.

Исправлена пропущенная минимальная схема bot-runtime тестов после V53.
В изолированном PostgreSQL 16 Testcontainers и unit tests прошли 170/170
случаев, native Maven exit=0. Рабочая БД, backfill, flags, deploy,
staging/commit/push не выполнялись; задача остаётся 🟡.

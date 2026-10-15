# 01-278 R91: подтверждённые вложения business-карточки

- Время: 2026-10-15 09:17:03 MSK.
- Промт пользователя: «дальше».
- Файлы: `BusinessScopedTicketReadService`, `BusinessScopeReadController`,
  `business-dialogs.js`, `businesses/dialogs.html`, PostgreSQL и WebMvc tests,
  `ai-context/tasks/task-details/01-278.md`.

Карточка обращения получила ссылку на файл записи истории. Новый download
маршрут ищет public ticket, точного владельца истории и metadata файла внутри
текущего business scope; непроверенные, внешние и неоднозначные raw-ID/key
ссылки остаются недоступными. Файл отдаётся как attachment с `no-store` и
`nosniff`, без перенаправления на legacy download API.

Чистый targeted Maven `clean test`: 9/9 GREEN (5 PostgreSQL и 4 WebMvc),
`node --check`, `git diff --check` GREEN. Рабочая БД, миграция/backfill,
feature flags, deployment, staging, commit/push не выполнялись. Задача
остаётся 🟡, решения S2B/product PENDING.

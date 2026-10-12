# 01-278: точный ticket ID в связях задач bot-core (R37)

Дата и время: 2026-10-12 12:44:22, Europe/Moscow.

Промт пользователя: `дальше. небольшим пакетом`

## Изменение

- `TaskService` больше не применяет нормализацию person identity к списку
  ticket IDs при создании и обновлении задачи. `T-EXACT`/` T-EXACT ` и
  `null`/` null ` остаются разными ссылками; точные дубликаты остаются
  одним `task_link`, blank IDs игнорируются. Нормализация людей не менялась.
- Новый test проверяет фактические `TaskLink` entities, а не только входной
  payload. Отдельный вход `AutoCloseFollowUpTaskService` пока не менялся.

## Проверка и границы

- Baseline HEAD=origin/main `ade882383b680e6e7098bfbf582db236cdda9844`;
  предыдущий R36 commit проверен по SHA, parent/message/4 файлам.
  Root был чистым, изменяемые blobs pinned. Detached worktree baseline:
  1 test, 1 failure — 2 links вместо 4. После исправления Maven wrapper
  `-pl :bot-core -am`: 4/4 теста GREEN в двух классах, 0 errors/skipped.
  Тесты используют mocks; рабочая БД и внешние сервисы не вызывались.
- Sandbox exact-file scope и diff --check GREEN. Guarded перенос после fresh
  root/blob check; staging/commit/push/deploy агентом не выполнялись.
- Это S2A source checkpoint, не business access или production acceptance.
  Вложение, auto-close entry, transport outbox/ledger остаются отдельными
  границами. 01-278 🟡; S2B/R12 PENDING/PROPOSED_NOT_APPROVED.
  Assignment, migration/backfill, flags и runtime не менялись.

## Изменённые файлы

- `java-bot/bot-core/src/main/java/com/example/supportbot/service/TaskService.java`
- `java-bot/bot-core/src/test/java/com/example/supportbot/service/TaskServiceExactTicketLinkTest.java`
- `ai-context/tasks/task-details/01-278.md`
- `ai-context/changelog/2026-10-12_124422_01-278_bot_task_exact_ticket_links.md`

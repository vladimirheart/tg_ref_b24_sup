# 01-278: точный ticket ID в статистике профиля клиента (R36)

Дата и время: 2026-10-12 12:34:07, Europe/Moscow.

Промт пользователя: `дальше`

## Фактическое изменение

- Периодная аналитика профиля клиента (WoW/MoM/YoY) дедуплицирует строки
  только по точному nonblank legacy ticket ID. Padded/plain и literal
  `null`/` null ` больше не сливаются в одну заявку; повтор одного ID
  остаётся одной заявкой. Даты, категории и прочие метрики не менялись.
- Добавлен targeted regression с двумя парами разных opaque IDs,
  дубликатом точного ID и blank строкой.

## Проверка и ограничения

- Baseline HEAD=origin/main `c650fb9a6011fa5ff50b2a05dafcfd5a76149f58`;
  предыдущий R35 commit проверен по parent/message/6 файлам. Root был чистым,
  source/test/task blobs pinned. Detached worktree baseline test RED:
  currentCount=2 вместо 4. После исправления 7/7 тестов в 4 классах GREEN:
  ClientsServiceTest, ClientsControllerWebMvcTest и два client-profile UI
  source contracts. PostgreSQL только временный Testcontainers.
- Sandbox exact-file scope и diff --check GREEN. Sass-generated CSS — лишь
  Windows EOL status, без content diff, в пакет не входит.
- Это source checkpoint S2A, не business authorization или production
  acceptance. Рабочая БД, схема, assignment, migration/backfill, flags,
  runtime, external integrations и deploy не менялись. 01-278 🟡;
  S2B/R12 остаются PENDING/PROPOSED_NOT_APPROVED.
- Staging, commit и push агентом не выполнялись.

## Изменённые файлы

- `spring-panel/src/main/java/com/example/panel/service/ClientsService.java`
- `spring-panel/src/test/java/com/example/panel/service/ClientsServiceTest.java`
- `ai-context/tasks/task-details/01-278.md`
- `ai-context/changelog/2026-10-12_123407_01-278_client_trend_exact_ticket_identity.md`

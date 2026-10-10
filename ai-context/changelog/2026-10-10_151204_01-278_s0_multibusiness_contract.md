# S0-контракт мультибизнесовой архитектуры Iguana

- Время: `2026-10-10T15:12:04+03:00` (Europe/Moscow).
- Промт пользователя: `выполни задачу 01-278`.
- Тип изменения: source-only S0 документация и task metadata; миграции,
  backfill, runtime, production, очереди, внешние системы, staging, commit и
  push не выполнялись.

## Изменённые файлы

- `ai-context/rules/03-multibusiness-s0-contract.md` — новый проектный
  контракт: canonical entities, scope guard, compatibility decisions, staged
  rollout boundary и test matrix.
- `ai-context/tasks/task-details/01-278.md` — зафиксирован S0 checkpoint,
  baseline аудита и нерешённые входные решения для S1.
- `ai-context/tasks/task-list.md` — задача переведена из `🟠` в `🟡`, поскольку
  выполняется S0 и дальнейшая реализация ещё не начата.
- `ai-context/changelog/2026-10-10_151204_01-278_s0_multibusiness_contract.md`
  — текущая append-only запись.

## Что фактически сделано

- На fresh `main`/`origin/main`
  `08c1bd0add49cd39d9d23dd6240d27f985607f23` проверены current source paths
  tickets/messages/history/attributes, page permissions, locations, shared
  channel templates, tasks, notifications и UI outbox.
- Зафиксировано, что legacy text `messages.business`, bare `ticket_id` paths и
  page-only authorization не являются достаточной основой для изоляции.
- Заданы deny-by-default business scope, отдельный `ALL_BUSINESSES` grant,
  managed `UNRESOLVED`/`GLOBAL_SYSTEM` states и запрет silent assignment.
- Не выполнены SQL/Flyway mutations, backfill, production rollout или обращения
  к внешним источникам. S1 заблокирован только до явных решений, перечисленных
  в S0 decision table; никаких эвристик для обхода этого gate не добавлено.

# 2026-04-15 15:55:18 — dialog quick actions split

## Что сделано

- добавлен `DialogQuickActionsController` для группы операторских quick actions:
  `reply`, `edit`, `delete`, `media`, `resolve`, `reopen`, `categories`,
  `take`, `snooze`;
- добавлен `DialogQuickActionService`, который инкапсулирует core-операции,
  уведомления участникам, работу с AI processing state и media attachment flow;
- соответствующие endpoints и request-records удалены из
  `DialogApiController`.

## Зачем

Это ещё один крупный bounded-context шаг для `dialogs`:

- `DialogApiController` теряет целый action-блок, а не только отдельные read
  endpoints;
- operator workflows начинают жить в отдельном controller/service слое;
- следующий split `workspace`, `ai` и `macro` сценариев становится проще,
  потому что action-level API уже вынесен отдельно.

## Проверка

- `spring-panel/mvnw.cmd -q -DskipTests compile`

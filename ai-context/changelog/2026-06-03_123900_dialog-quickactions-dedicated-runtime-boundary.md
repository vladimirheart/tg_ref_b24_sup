# 2026-06-03 12:39:00 — dialog quickactions dedicated runtime boundary

## Что сделано
- добавлен новый `DialogQuickActionsIntegrationTest` с live
  `SpringBootTest + SQLite` покрытием для HTTP quick actions;
- отдельно закреплён transport/runtime contract для `reassign`,
  `participants add/remove`, `resolve` и `reopen`;
- добрана downstream continuity после этих действий:
  `/api/dialogs`, `/api/dialogs/{ticketId}/participants` и
  `/api/dialogs/{ticketId}/workspace` теперь подтверждаются прямо после
  реальных controller-вызовов;
- зафиксирована важная runtime-семантика: после `reassign` новый owner
  наследует unread state, а не получает ticket автоматически в `in_work`.

## Проверка
- `spring-panel\.\mvnw.cmd '-Dtest=DialogQuickActionsIntegrationTest,DialogQuickActionsControllerWebMvcTest,DialogListIntegrationTest,DialogReadIntegrationTest,DialogWorkspaceIntegrationTest' test`

## Дальше
- добрать remaining operator action drift вокруг `take/categories/spam` и
  пост-action refresh loops на соседних consumer routes.

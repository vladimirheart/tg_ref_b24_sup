# 2026-05-27 07:42:00 — dialog read workspace notification refresh parity

## Что сделано
- `DialogReadIntegrationTest` расширен live
  `SpringBootTest + SQLite` сценарием, который связывает `/api/dialogs` list
  unread, `/api/dialogs/{ticketId}/history` и `/api/notifications`, чтобы
  закрепить read-marker loop для history route;
- `DialogWorkspaceIntegrationTest` добран соседним live сценарием для
  `/api/dialogs/{ticketId}/workspace` с тем же runtime циклом
  `list unread -> workspace read -> dialog unread reset -> explicit bell ack`;
- в обоих сценариях зафиксировано, что `history` и `workspace`, как и
  `details`, двигают `last_read_at` и гасят dialog unread projection, но не
  помечают bell notification прочитанной автоматически.

## Проверка
- `spring-panel\.\mvnw.cmd "-Dtest=DialogDetailsIntegrationTest,DialogReadIntegrationTest,DialogWorkspaceIntegrationTest,NotificationApiIntegrationTest" test"`

## Дальше
- добрать соседние consumer projections и remaining drift вокруг
  status/owner/action lifecycle уже после explicit ack и следующего
  follow-up refresh.

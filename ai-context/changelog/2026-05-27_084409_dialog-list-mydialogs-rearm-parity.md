# 2026-05-27 08:44:09 — dialog list my_dialogs rearm parity

## Что сделано
- `DialogDetailsIntegrationTest` расширен live
  `SpringBootTest + SQLite` сценарием на `queue/my_dialogs` buckets после
  explicit bell ack и повторного client follow-up;
- зафиксированы переходы одного operator-owned ticket по цепочке
  `my_dialogs.unanswered -> in_work -> unanswered` через `details` read,
  operator reply и следующий follow-up;
- дополнительно подтверждено, что `my_dialogs` buckets остаются согласованы с
  `statusKey` и `unreadCount`: `waiting_client + unreadCount=0` уводит
  диалог в `in_work`, а `waiting_operator + unreadCount=1` возвращает его в
  `unanswered`.

## Проверка
- `spring-panel\.\mvnw.cmd "-Dtest=DialogDetailsIntegrationTest,DialogReadIntegrationTest,DialogWorkspaceIntegrationTest,NotificationApiIntegrationTest" test"`

## Дальше
- добрать соседние consumer contracts и remaining projection drift вокруг
  `queue/status-owner` surfaces после repeated follow-up refresh.

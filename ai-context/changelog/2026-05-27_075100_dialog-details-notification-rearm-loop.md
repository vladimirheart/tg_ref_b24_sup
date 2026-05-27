# 2026-05-27 07:51:00 — dialog details notification rearm loop

## Что сделано
- `DialogDetailsIntegrationTest` расширен live
  `SpringBootTest + SQLite` сценарием на повторный клиентский follow-up после
  explicit `POST /api/notifications/{id}/read`;
- зафиксировано, что после первого `details` read и отдельного bell ack
  следующий follow-up снова поднимает `dialogs[0].unreadCount=1` и создаёт
  новый unread notification entry;
- дополнительно подтверждено, что `last_read_at` не “залипает” на первом
  цикле: после второго открытия `details` он обновляется до timestamp второго
  follow-up.

## Проверка
- `spring-panel\.\mvnw.cmd "-Dtest=DialogDetailsIntegrationTest,DialogReadIntegrationTest,DialogWorkspaceIntegrationTest,NotificationApiIntegrationTest" test"`

## Дальше
- добрать соседние queue/my-dialogs projections и remaining drift вокруг того
  же status/owner/action lifecycle после repeated follow-up refresh.

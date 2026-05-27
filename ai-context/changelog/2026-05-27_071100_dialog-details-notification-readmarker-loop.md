# 2026-05-27 07:11:00 — dialog details notification readmarker loop

## Что сделано
- `DialogDetailsIntegrationTest` расширен live
  `SpringBootTest + SQLite` сценарием, который связывает `/api/dialogs` list
  unread projection, `/api/dialogs/{ticketId}` read receipt и
  `/api/notifications` bell badge в одном runtime loop;
- зафиксировано, что после client follow-up список сначала показывает
  `unreadCount=1`, а `NotificationService.notifyDialogParticipants(...)`
  создаёт operator-facing bell notification с нормализованным
  `/dialogs/{ticketId}` URL;
- открытие `details` route двигает `last_read_at` и обнуляет dialog unread
  projection, но не помечает bell notification как прочитанную;
- bell unread badge сбрасывается только после explicit
  `/api/notifications/{id}/read`, что закрепляет разделение между
  dialog read-state и notification ack-state.

## Проверка
- `spring-panel\.\mvnw.cmd "-Dtest=DialogDetailsIntegrationTest,DialogReadIntegrationTest,DialogWorkspaceIntegrationTest,NotificationApiIntegrationTest" test"`

## Дальше
- добрать соседние consumer projections и remaining projection drift вокруг
  того же status/owner/action lifecycle после read refresh.

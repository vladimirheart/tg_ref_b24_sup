# 2026-06-15 10:13:34 - notification read-all rearm loop

## Что сделано
- расширен runtime coverage вокруг `POST /api/notifications/read-all`:
  добавлен bell-level rearm сценарий в
  `NotificationApiIntegrationTest`, который подтверждает, что после
  mass-ack следующее уведомление для того же identity снова поднимает
  `unread_count` и остаётся unread в notification list;
- добавлены live read-side сценарии в
  `DialogReadIntegrationTest`, `DialogDetailsIntegrationTest` и
  `DialogWorkspaceIntegrationTest`, которые фиксируют одну и ту же
  семантику:
  `read-all -> reread consumer -> next follow-up -> unread/bell rearm`;
- подтверждено, что после reread через `history/details/workspace`
  следующий клиентский follow-up снова возвращает диалог в
  `my_dialogs.unanswered`, поднимает row-level `unreadCount` и создаёт
  новый bell unread entry, то есть mass-ack не ломает следующий
  refresh loop.

## Почему это важно
- `read-all` уже был отделён от dialog reread semantics, но без этого
  пакета оставался тонкий риск, что mass-ack "приглушит" следующий
  follow-up loop для bell badge или `my_dialogs` bucket-ов;
- новый пакет добирает именно repeated refresh continuity после
  mass-ack и снижает regression-риск в `dialog-read/workspace/details`
  зоне.

## Проверка
- `./mvnw.cmd -Dtest=NotificationApiIntegrationTest,DialogReadIntegrationTest,DialogDetailsIntegrationTest,DialogWorkspaceIntegrationTest test`

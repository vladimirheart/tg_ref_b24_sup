# 2026-06-15 15:31:26 - http quick actions adjacent consumers

## Что сделано
- расширен runtime hardening вокруг adjacent read-consumer'ов:
  `DialogListIntegrationTest`, `DialogReadIntegrationTest` и
  `DialogDetailsIntegrationTest` переведены с прямых
  `DialogQuickActionService` вызовов на реальные quick-action HTTP
  round-trips;
- `DialogListIntegrationTest` теперь подтверждает:
  `POST /api/dialogs/{ticketId}/reassign` сразу отражается в `/api/dialogs`
  для old/new owner, а отдельный live сценарий закрепляет
  `POST /resolve -> POST /reopen` lifecycle прямо на list consumer;
- `DialogReadIntegrationTest` теперь проходит `reassign ->
  DELETE /participants/{username}` через HTTP и reread'ит
  `/api/dialogs/{ticketId}/participants`, чтобы participants projection не
  держался на service-level shortcut;
- `DialogDetailsIntegrationTest` теперь читает summary/history/categories
  после реального `POST /reassign -> POST /resolve -> POST /reopen`, а не
  после прямой orchestration через service.

## Почему это важно
- раньше часть adjacent consumer coverage всё ещё подтверждала lifecycle
  через прямые service вызовы, поэтому оставался риск, что controller-level
  payload или audit/permission boundary разойдутся с тем, что читает
  `list/details/participants`;
- новый пакет закрепляет целостный HTTP contract между quick-action boundary
  и read-side consumers и сужает remaining drift вокруг operator action
  surfaces.

## Отдельное наблюдение
- live list scenario показал важную текущую семантику reopen:
  после `POST /reopen` dialog возвращается в `my_dialogs.in_work` с
  `unreadCount=0`, но `statusKey` в `/api/dialogs` проецируется как
  `waiting_client`, а не `waiting_operator`.

## Проверка
- `./mvnw.cmd -Dtest=DialogListIntegrationTest,DialogReadIntegrationTest,DialogDetailsIntegrationTest test`

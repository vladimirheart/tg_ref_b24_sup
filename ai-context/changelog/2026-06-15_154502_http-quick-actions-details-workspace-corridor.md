# 2026-06-15 15:45:02 - http quick actions details workspace corridor

## Что сделано
- добран оставшийся adjacent runtime хвост в
  `DialogDetailsIntegrationTest` и `DialogWorkspaceIntegrationTest`:
  интеграционные сценарии переведены с прямых вызовов
  `DialogQuickActionService` на реальные quick-action HTTP round-trips;
- `DialogDetailsIntegrationTest` теперь подтверждает через live controller
  boundary, что `POST /api/dialogs/{ticketId}/reassign` сразу переносит
  ownership между `my_dialogs`, а `POST /resolve -> POST /reopen`
  корректно скрывает и возвращает dialog в list projection;
- `DialogWorkspaceIntegrationTest` теперь проходит полный
  `reassign -> resolve -> reopen -> participants_remove` lifecycle через
  `POST`/`DELETE /api/dialogs/...` и reread'ит `/workspace`, чтобы guards,
  conversation status и participants projection проверялись на реальном
  HTTP contract;
- mass-ack сценарий в `DialogDetailsIntegrationTest` стабилизирован:
  `POST /api/notifications/read-all` теперь сверяется с фактическим
  unread bell count из БД перед ack вместо хрупкого ожидания `updated=1`.

## Почему это важно
- до этого `details/workspace` corridor всё ещё частично опирался на
  service-level shortcuts, поэтому controller payload, permission boundary
  и read-side projection могли расходиться без падения тестов;
- новый пакет замыкает adjacent consumer coverage вокруг одного и того же
  HTTP quick-action boundary и убирает лишнюю зависимость от fixture-seeded
  notification count в массовом bell ack.

## Проверка
- `./mvnw.cmd -Dtest=DialogDetailsIntegrationTest,DialogWorkspaceIntegrationTest test`

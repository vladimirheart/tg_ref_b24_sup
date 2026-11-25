# 2026-11-25 — Решения unblock-заявок PostgreSQL, задача 01-313

## Промт пользователя

> http://127.0.0.1:8080/unblock-requests висит один запрос, но:
> а) при попытке одобрить или отклонить, возвращает "Внутренняя ошибка сервера"
> б) в карточке клиента активна кнопка "Заблокировать", т.е. система не видит блокировки.
> значит либо заявка старая и багнутая либо панель некорректно обработала статус.

## Причина

Заявка №1 от 28 октября осталась pending при отсутствии блокировки клиента.
Оба решения падали из-за integer 0/1 в boolean-полях PostgreSQL. Прежний тест
создавал INTEGER fixture и не воспроизводил типы рабочей схемы.

## Изменения

- UnblockRequestService использует boolean параметры и TRUE/FALSE литералы.
- Решение блокирует blacklist перед заявкой и повторно проверяет status/user_id.
- Заявка уже разблокированного клиента становится expired с пояснением без
  фиктивного события разблокировки или сообщения клиенту.
- V105 сохраняет историю, завершает obsolete pending заявки и очищает их flags,
  не меняя действующие блокировки и обработанные заявки.
- AccessRecoveryPostgresqlTest переведён на BOOLEAN fixture, добавлен
  UnblockRequestPostgresqlTest для approve/reject/stale/concurrency/rollback/repair.
- Правило 11-client-unblock-lifecycle.md фиксирует domain и PostgreSQL contract;
  задача 01-313 завершена статусом 🟣.

## Валидация и выпуск

- 36 targeted PostgreSQL/WebMvc/security tests успешны; полная Flyway-цепочка
  до V105 проверена на изолированной БД. Source проверен в detached worktree.
- Новый image `iguana-panel:unblock-fix-711c0436c080-20261125-121000`, digest
  fc9a76d751f2, развёрнут только на panel-web и одноразовом db-migrate.
- V105 применена с exit=0. Заявка №1 expired, pending count=0, клиент остаётся
  незаблокированным. Panel healthy, readiness 200/UP.
- PostgreSQL, worker и supervisor ботов не перезапускались; child-пулы сохранены.
- Rollback image/override сохранены, клиенту тестовые сообщения не отправлялись.
- Staging/commit/push не выполнялись. git diff --check успешен.

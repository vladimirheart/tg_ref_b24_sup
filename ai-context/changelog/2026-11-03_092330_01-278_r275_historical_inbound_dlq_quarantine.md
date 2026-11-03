# 01-278 / R275 — общий health восстановлен точечным карантином DLQ

Промт пользователя:

> ты пишешть: "Общий health по-прежнему DOWN из-за старого inbound DLQ1" исправь

## Диагностика и действие

Clean HEAD/origin/main/actual remote eb64df46b9f22b7769c7805b829686ced4f9f212,
рабочий тестовый экземпляр этой Windows/Docker-сессии. В inboundDLQ один ready,
unacked0/consumers0. Его219-byte JSON имеет форму outbound-feedback DTO, но
eventType/requestId/prompt NULL и eventKind отсутствует. X-death rejected1,
2026-10-28T12:35:36Z, исходный inbound.max route. В БД тот же ID соответствует
published client_message.active_ticket; inbox/ledger для ID отсутствуют,
exact root существует, pending истёк, rating имеется. Outbox не удалён и событие
не объявлено успешно обработанным. Недостающие поля/клиентский контент не выдумывались.

Исходные payload/HTTP envelope сохранены до действия; дополнительно сохранён
полный AMQP wire header/body с lossless header roundtrip, disk flush и readback.
Приватный same-host архив .tmp/01-278-r275-dlq/private/ вне Git, NTFS ACL только
текущему пользователю; сохранить при cleanup .tmp. Payload SHA256
0717C412B0BF96F0881BEFBEAF3BB60631D410E88A3400BDC002736EFD9DF4D2,
message.amqp SHA2569EFE1108A7D40991A9EB86F0D4A5065A3E3D50DBD69B9773FF468B4251C1292E.
Архив позволяет восстановить экземпляр, реальный restore/replay не выполнялся.

Оператор basicGet/manual ACK ограничен exact queue/219 bytes/SHA/event ID/route/
properties, без consumers и параллельного backlog. Сначала validate с NACK/requeue;
затем apply сравнивает полный wire archive до ACK ровно одного delivery09:23 MSK.
При mismatch до ACK закрытие connection возвращает unacked сообщение в DLQ.
HTTP inspection также делал get/requeue; диагностика не названа строго read-only.
Нет queue purge, реконструкции/replay, новых очередей или клиентских отправок.
Порог/реализация health не менялись.

Первые SQL-пробы остановились на неверных именах columns/tables в read-only
транзакциях; исправлены до действия. Первая validate сохранила архив и NACK,
но остановилась на асинхронном ready count; повторная проверка с bounded ожиданием
прошла, архив не перезаписывался. До apply queue1 и unacked0 подтверждены.
Offline self-test5 проверок включает mismatch deny и AMQP header roundtrip.

## Приёмка и границы

- Общий health200/UP, readiness200/UP, production-readiness200/ready/Rabbit healthy.
- Все DLQ0; topology/counts/consumers остальных очередей exact.61 свежая live
  login/access/SSE/readiness проверка GREEN, сессия закрыта.
- Все18 контейнеров/IDs/images/start/restart/ENV/mounts/HostConfig прежние,
  конфигурационные inputs exact (обычный backup-policy heartbeat отдельно).
- V85/85/settings455/accounts/grants/roots53/pending51/ratings36/outbox/inbox/
  ledger17delivered/receipt0 fingerprints exact;4 старых failed inbox не менялись.
- Новых ERROR0, worker WARN1: Notion knowledge sync invalid API token — отдельно,
  не исправлялся. Остальные прежние operational/functional gates не закрываются.
- Rebuild/restart/deploy/migrations/backfill/flags/provider sends/commit/push нет.
  Runtime web/worker R274/R273/f78708f35/image826bf025417d; runner R272/R271/044d.

Task/S0/BOT checkpoint обновлены через isolated metadata source/replay. Исторический
task tail от Source R273 неизменён; старые health DOWN описывают прошлые этапы.
Задача🟡/S6 и пять крупных блоков остаются; S2B снятR190. Evidence/operators:
.tmp/01-278-r275-dlq/sealed-proof.json/inspection.json/correlation.json/
validate-result.json/apply-result.json/health-result.json/access-result.json.

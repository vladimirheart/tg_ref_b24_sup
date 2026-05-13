# 2026-05-13 07:13:18 — remove-mtproto-from-network-routes

## Что сделано

- Убран `MTProto` из форм сетевых маршрутов и профилей на странице настроек.
- Удалены `secret`-поля, UI-валидация и описания, относившиеся только к `MTProto`.
- Очищен runtime contract Telegram-бота: больше нет fallback/warning-логики и специальных env-маркеров для `MTProto`.
- Обновлены целевые тесты панели под итоговый поддерживаемый набор схем прокси.

## Зачем

`MTProto` в текущей архитектуре Telegram Bot API runtime не давал рабочего результата и создавал ложное ощущение поддерживаемого режима. Пользователь явно подтвердил, что такой режим нужно убрать.

## Проверка

- `spring-panel`: `./mvnw.cmd -q "-Dnet.bytebuddy.experimental=true" "-Dtest=IntegrationNetworkServiceTest,BotRuntimeContractServiceTest" test`
- `java-bot`: `./mvnw.cmd -q -pl bot-telegram -am -DskipTests compile`

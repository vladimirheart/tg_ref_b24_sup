# 2026-09-01 14:21 — задача 01-232: координация Telegram long polling по token fingerprint

## Пользовательский запрос

`продолжай`

## Выполнено

- Установлена причина исторического Telegram `409 Conflict`: Redis lease использовал `GROUP_CHAT_ID`, а не identity бота.
- Для Telegram lease теперь применяется SHA-256 fingerprint токена. Один token с разными group chat получает один и тот же Redis key; токен не раскрывается.
- Добавлены overloads координационного сервиса для строковой ingress identity и контрактный тест fingerprint.

## Проверка

- `java-bot\\mvnw.cmd -pl bot-telegram -am -Dtest=BotIngressCoordinationServiceTest,BotPropertiesTest,SupportBotTest,SupportBotChoiceInputContractTest -Dsurefire.failIfNoSpecifiedTests=false test` — 9 tests, 0 failures.

## Отложенная production-проверка

В момент выполнения отсутствуют запущенные Java bot runtime, а `postgres` и `rabbitmq` остановлены. Для восстановления сервиса и проверки реального отсутствия новых `409` создана задача `01-235`.

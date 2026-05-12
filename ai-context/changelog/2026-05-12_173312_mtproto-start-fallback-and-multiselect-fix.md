# 2026-05-12 17:33:12 — MTProto start fallback and multiselect fix

## Затронутые файлы

- `ai-context/tasks/task-list.md`
- `spring-panel/src/main/java/com/example/panel/service/BotRuntimeContractService.java`
- `spring-panel/src/main/java/com/example/panel/service/IntegrationNetworkService.java`
- `java-bot/bot-telegram/src/main/java/com/example/supportbot/telegram/SupportBot.java`
- `spring-panel/src/main/resources/templates/settings/index.html`
- `spring-panel/src/test/java/com/example/panel/service/BotRuntimeContractServiceTest.java`
- `spring-panel/src/test/java/com/example/panel/service/IntegrationNetworkServiceTest.java`

## Пользовательский промт

1. `Не удалось запустить бота: Не удалось запустить бота: MTProto proxy сохранён в настройках, но текущий Telegram runtime работает через Bot API over HTTPS и не поддерживает MTProto напрямую. Нужен внешний HTTP/SOCKS-адаптер.`
2. `дополнительно: нет возможности добавить больше одной записи в настройках сетевого маршрута в настройках бота`

## Что сделано

- Убран hard-fail старта Telegram-бота при сохранённом `MTProto` маршруте.
- Для Telegram runtime `MTProto` теперь обрабатывается как direct fallback с явным warning вместо остановки процесса.
- Для panel HTTP client убран exception при `MTProto`: используется direct fallback с предупреждением в лог.
- В runtime environment добавлен явный маркер `APP_NETWORK_UNSUPPORTED_PROXY_SCHEME=mtproto`, чтобы бот мог логировать причину fallback.
- Улучшен UX выбора профильной цепочки маршрута:
  - multi-select теперь поддерживает выбор нескольких профилей обычными кликами;
  - повторный клик снимает выбор;
  - подсказки в UI обновлены под новый способ выбора.
- Обновлены тесты на новый fallback-контракт и direct fallback для panel HTTP client.

## Проверка

- `spring-panel`: `./mvnw.cmd -q "-Dnet.bytebuddy.experimental=true" "-Dtest=IntegrationNetworkServiceTest,BotRuntimeContractServiceTest" test`
- `java-bot`: `./mvnw.cmd -q -pl bot-telegram -am -DskipTests compile`

# 2026-05-12 17:03:20 — MTProto route creation and runtime guard

## Затронутые файлы

- `ai-context/tasks/task-list.md`
- `ai-context/tasks/task-details/01-080.md`
- `spring-panel/src/main/resources/templates/settings/index.html`
- `spring-panel/src/main/java/com/example/panel/service/IntegrationNetworkService.java`
- `spring-panel/src/main/java/com/example/panel/service/BotRuntimeContractService.java`
- `spring-panel/src/test/java/com/example/panel/service/IntegrationNetworkServiceTest.java`
- `spring-panel/src/test/java/com/example/panel/service/BotRuntimeContractServiceTest.java`
- `java-bot/bot-telegram/src/main/java/com/example/supportbot/telegram/SupportBot.java`

## Пользовательский промт

1. `в настройках ботов есть возможность настраивать проксирование. изучи протокол MTPROTO и добавь его в возможность создания. прежде чем изменять файлы в проекте - напиши план`
2. Значимое уточнение по реализации: сначала подтвердить реальные ограничения MTProto относительно текущего Telegram Bot API runtime и не добавлять фиктивную поддержку.

## Что сделано

- Добавлена новая задача `01-080` в `ai-context/tasks` и оформлена её детализация.
- В модель маршрутов и прокси-профилей добавлена схема `mtproto` и отдельное поле `secret`.
- В UI настроек добавлены:
  - выбор `MTProto` для project/bots/channel routes и integration profiles;
  - отдельные поля `secret`;
  - пояснения и клиентская валидация для `MTProto`.
- В backend-контракте маршрутов:
  - `MTProto` сохраняется как конфигурационный тип;
  - для него не генерируются `HTTP_PROXY` / `HTTPS_PROXY` / `ALL_PROXY` / Java proxy flags;
  - `HTTP`-клиент панели и Telegram bot runtime явно отказываются использовать `MTProto` напрямую.
- В runtime и контракте запуска добавлена диагностика: если для Telegram выбран `MTProto`, пользователь получает явное сообщение о необходимости внешнего адаптера.
- Добавлены тесты на:
  - environment contract для `mtproto`;
  - отказ `HTTP`-клиента и Telegram runtime от прямого использования `MTProto`;
  - warning/blocker в runtime contract.

## Проверка

- `spring-panel`: `./mvnw.cmd -q "-Dnet.bytebuddy.experimental=true" "-Dtest=IntegrationNetworkServiceTest,BotRuntimeContractServiceTest" test`
- `java-bot`: `./mvnw.cmd -q -pl bot-telegram -am -DskipTests compile`

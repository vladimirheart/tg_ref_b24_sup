# 2026-05-22 22:41:46 — Telegram legacy mirror proxy fallback

## Затронутые файлы

- `spring-panel/src/main/java/com/example/panel/controller/ChannelApiController.java`
- `spring-panel/src/main/java/com/example/panel/service/BotRuntimeContractService.java`
- `spring-panel/src/main/java/com/example/panel/service/IntegrationNetworkService.java`
- `spring-panel/src/test/java/com/example/panel/controller/ChannelApiControllerWebMvcTest.java`
- `spring-panel/src/test/java/com/example/panel/service/BotRuntimeContractServiceTest.java`
- `spring-panel/src/test/java/com/example/panel/service/IntegrationNetworkServiceTest.java`

## Пользовательский промпт

1. `в логе телеграм-бота: ... Unable to tunnel through proxy ... 405 Not Allowed`
2. `при этом если спросить по прямому url в браузере (https://telegram.ftl-dev.ru/bot.../getme), информация возвращается корректная`

## Что сделано

- Добавлен backward-compatible fallback для старых Telegram-каналов, где Bot API mirror был ошибочно сохранён как `network_route.proxy`, а отдельный `platform_config.base_url` ещё не заполнен.
- `IntegrationNetworkService` теперь умеет распознавать legacy-сценарий `https://telegram...:443` и возвращать его как Telegram Bot API base URL.
- Panel-side Telegram `getMe` и `sendMessage` больше не пытаются использовать такой endpoint как CONNECT-proxy и обращаются к нему как к Bot API mirror.
- Runtime-контракт для отдельного Java Telegram bot-процесса теперь подставляет `TELEGRAM_BOT_API_BASE_URL` из legacy proxy-конфига и переводит сетевой маршрут в `direct`, чтобы не было `Unable to tunnel through proxy`.
- Добавлены тесты на helper legacy mirror, panel-side `getMe` URL и runtime env fallback.

## Проверка

- `spring-panel`: `./mvnw.cmd -q "-Dnet.bytebuddy.experimental=true" "-Dtest=IntegrationNetworkServiceTest,BotRuntimeContractServiceTest,ChannelApiControllerWebMvcTest" test`

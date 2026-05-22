# 2026-05-22 16:05:00 — Telegram base URL and startup failure contract

## Затронутые файлы

- `ai-context/tasks/task-list.md`
- `ai-context/tasks/task-details/01-108.md`
- `spring-panel/src/main/java/com/example/panel/controller/ChannelApiController.java`
- `spring-panel/src/main/java/com/example/panel/service/BotRuntimeContractService.java`
- `spring-panel/src/main/resources/templates/settings/index.html`
- `spring-panel/src/test/java/com/example/panel/controller/ChannelApiControllerWebMvcTest.java`
- `spring-panel/src/test/java/com/example/panel/service/BotRuntimeContractServiceTest.java`
- `java-bot/bot-telegram/pom.xml`
- `java-bot/bot-telegram/src/main/java/com/example/supportbot/telegram/SupportBot.java`
- `java-bot/bot-telegram/src/main/java/com/example/supportbot/telegram/TelegramLongPollingLifecycle.java`
- `java-bot/bot-telegram/src/test/java/com/example/supportbot/telegram/SupportBotTest.java`

## Пользовательский промпт

1. `в настройках бота есть возможность указать настройки прокси. в телеграм-боте ставлю заведомо рабочие параметры, и панель говорит что бот якобы запущен, но в логе возвращает: "2026-05-22 15:27:46.331+03:00 INFO  [main] c.e.supportbot.telegram.SupportBot - Telegram client will use proxy https://telegram.ftl-dev.ru:443 2026-05-22 15:27:46.697+03:00 INFO  [main] o.a.http.impl.execchain.RetryExec - I/O exception (java.io.IOException) caught when processing request to {s}->https://api.telegram.org:443: Unable to tunnel through proxy. Proxy returns "HTTP/1.1 405 Not Allowed""`
2. `при этом если спросить по прямому url в браузере (https://telegram.ftl-dev.ru/bot8391583658:AAH9du_m0xGwhJtbvRKpHtMTgyaTRUFkzYU/getme), информация возвращается корректная`

## Что сделано

- Добавлена отдельная поддержка `Telegram Bot API base URL` для channel `platform_config` и runtime env `TELEGRAM_BOT_API_BASE_URL`.
- `spring-panel` теперь использует Telegram `base URL` и для `getMe`, и для тестовой отправки сообщений.
- `bot-telegram` переключён на `DefaultBotOptions.setBaseUrl(...)`, чтобы mirror/self-hosted Bot API использовался штатно, а не через proxy-контракт.
- Старт Telegram runtime перестал скрывать фатальные ошибки инициализации: при провале `getMe` или long polling процесс теперь падает с понятным сообщением, поэтому панель больше не показывает ложный статус `running`.
- В текст ошибки добавлена отдельная подсказка для кейса `Unable to tunnel through proxy`: если прямой `/bot<TOKEN>/getMe` работает, endpoint надо настраивать как `base URL`, а не как proxy.
- UI настроек канала дополнен Telegram-specific полем для `base URL`.
- Добавлены тесты на panel-side контракт и на построение Telegram Bot API base URL в runtime.

## Проверка

- `spring-panel`: `./mvnw.cmd -q "-Dnet.bytebuddy.experimental=true" "-Dtest=ChannelApiControllerWebMvcTest,BotRuntimeContractServiceTest" test`
- `java-bot`: `./mvnw.cmd -q -pl bot-telegram -am test`

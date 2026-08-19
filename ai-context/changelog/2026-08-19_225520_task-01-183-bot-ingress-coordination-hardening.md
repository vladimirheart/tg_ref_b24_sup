# 2026-08-19 22:55:20 — task-01-183-bot-ingress-coordination-hardening

## Что изменено

- добавлен shared bot ingress coordination layer:
  - `java-bot/bot-core/src/main/java/com/example/supportbot/config/BotIngressCoordinationProperties.java`
  - `java-bot/bot-core/src/main/java/com/example/supportbot/service/BotIngressCoordinationService.java`
  - `java-bot/bot-core/src/main/resources/application.yml`
  - `java-bot/bot-core/pom.xml`
- long-poll bot runtimes переведены на explicit active-owner semantics:
  - `java-bot/bot-telegram/src/main/java/com/example/supportbot/telegram/TelegramLongPollingLifecycle.java`
  - `java-bot/bot-telegram/src/main/java/com/example/supportbot/telegram/SupportBot.java`
  - `java-bot/bot-vk/src/main/java/com/example/supportbot/vk/VkSupportBot.java`
  - `java-bot/bot-vk/src/main/java/com/example/supportbot/vk/VkCallbackController.java`
  - `java-bot/bot-max/src/main/java/com/example/supportbot/max/MaxLongPollingLifecycle.java`
  - `java-bot/bot-max/src/main/java/com/example/supportbot/max/MaxWebhookController.java`
- добавлена точечная верификация:
  - `java-bot/bot-core/src/test/java/com/example/supportbot/service/BotIngressCoordinationServiceTest.java`
- синхронизирована документация и статус задачи:
  - `docs/runbooks/postgresql-production-contour.md`
  - `docs/POSTGRESQL_FULL_PRODUCTION_GAP_AUDIT.md`
  - `docs/environment_variables.md`
  - `docs/configuration.md`
  - `ai-context/tasks/task-details/01-183.md`

## Пользовательский промпт

Инициирующий промпт:

> хорошо, давай что осталось добивать

## Кратко по сути

- `java-bot` больше не должен жить как некоординированный multi-instance ingress race в long-poll режимах;
- ingress leadership для `Telegram` / `VK` / `MAX` теперь завязан на shared Redis lease и active-owner semantics;
- bot-side schedulers, которые завязаны на ingress owner state, больше не исполняются каждым instance без координации;
- remaining residual gap после этого пакета сузился до strict active-active webhook/session-sharing scenarios, где state ещё не externalized полностью.

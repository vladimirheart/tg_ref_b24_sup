# 2026-07-21 16:24:39 — bot settings derived compatibility dto

## Контекст
- Пользователь: `продолжай`
- Значимый контекст из `01-150`: после каноникализации `auto_close_config` следующим крупным этапом оставался cleanup derived/deprecated mirror-полей `question_flow` / `rating_system` в runtime и публичном bot settings contract.

## Что сделано
- В `java-bot/bot-core/src/main/java/com/example/supportbot/settings/dto/BotSettingsDto.java` compatibility mirrors `question_flow` и `rating_system` переведены в derived output:
  - они больше не живут как самостоятельные source-of-truth поля;
  - getter-ы вычисляют эти значения из активных `question_templates` / `rating_templates`;
  - legacy setter-ы оставлены как fallback для совместимости.
- В `java-bot/bot-core/src/main/java/com/example/supportbot/settings/BotSettingsService.java` убрана обратная дорисовка `question_flow` / `rating_system` в промежуточные sanitized maps.
- В `java-bot/bot-core/src/main/java/com/example/supportbot/settings/BotSettingsService.java` добавлен явный helper `questionFlow(BotSettingsDto settings)`, чтобы runtime читал active template flow через service-level API, а не через legacy-shaped mirror.
- Runtime-модули переведены на новый helper:
  - `java-bot/bot-telegram/src/main/java/com/example/supportbot/telegram/SupportBot.java`
  - `java-bot/bot-vk/src/main/java/com/example/supportbot/vk/VkSupportBot.java`
  - `java-bot/bot-max/src/main/java/com/example/supportbot/max/MaxWebhookController.java`
- В `java-bot/bot-core/src/test/java/com/example/supportbot/settings/BotSettingsServiceTest.java` добавлены regression tests:
  - active question template должен побеждать legacy root mirror;
  - `BotSettingsDto` должен сериализовать derived compatibility mirrors из canonical templates.
- В `ai-context/tasks/task-details/01-150.md` обновлены execution log, точка остановки и следующий шаг: runtime уже отвязан от mirror-полей, дальше решение смещается в публичный JSON-contract `BotSettingsDto` и оставшийся `auto_close_hours` fallback.

## Проверки
- `java-bot\\mvnw.cmd -pl bot-core "-Dtest=BotSettingsServiceTest" test`
- `java-bot\\mvnw.cmd -pl bot-telegram,bot-vk,bot-max -am -DskipTests compile`

## Следующий шаг
- Отдельно решить, можно ли уже убирать root-level `question_flow` / `rating_system` из публичного JSON-выхода `BotSettingsDto`, или сначала нужен совместимый deprecation window для fixtures/consumers; после этого добить removal legacy `auto_close_hours` read-fallback в `MaintenanceTasks`.

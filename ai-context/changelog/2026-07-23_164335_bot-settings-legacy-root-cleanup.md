# 2026-07-23 16:43:35 — bot settings legacy-root cleanup

## User prompt

> давай
>
> делай

## What changed

- В `java-bot/bot-core/src/main/java/com/example/supportbot/settings/dto/BotSettingsDto.java` удалены legacy DTO-хвосты `legacyQuestionFlow` и `legacyRatingSystem`, а derived-accessors `getQuestionFlow()` и `getRatingSystem()` теперь резолвят значения только из активных canonical templates.
- В `java-bot/bot-core/src/main/java/com/example/supportbot/settings/BotSettingsService.java` убрана специальная legacy-root логика вокруг deprecated `bot_settings.question_flow` и `bot_settings.rating_system`; runtime продолжает работать только с `question_templates` и `rating_templates`.
- В `spring-panel/src/main/java/com/example/panel/service/BotSettingsPayloadNormalizer.java` упрощена нормализация bot settings: deprecated root keys `question_flow` и `rating_system` больше не участвуют в mirror-comparison/logging и просто вычищаются из входного payload.
- В `spring-panel/src/main/resources/static/js/bot-settings.js` удалена frontend compatibility-ветка для transitional question template payload через `questionFlow` и `questions`; редактор шаблонов теперь принимает только canonical `question_flow`.
- В `java-bot/bot-core/src/test/java/com/example/supportbot/settings/BotSettingsServiceTest.java` обновлены helper-тесты после удаления legacy DTO-setters.

## Why

- После аудита стало ясно, что поддержка root `bot_settings.question_flow` и `bot_settings.rating_system` больше не нужна как runtime-contract и только поддерживает лишнюю compatibility-сложность.
- При этом `channel.question_template_id`, `channel.rating_template_id`, `questions_cfg` и `dialog_config.question_templates` сознательно не трогались: это другой слой контракта, который ещё используется.

## Validation

- `./mvnw.cmd "-Dtest=SettingsTopLevelUpdateServiceTest,SettingsUpdateSharedConfigIntegrationTest,ManagementControllerWebMvcTest,SettingsPageDataServiceTest,SettingsApiControllerWebMvcTest" test` in `spring-panel`
- `./mvnw.cmd -pl bot-core "-Dtest=BotSettingsServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" clean test` in `java-bot`

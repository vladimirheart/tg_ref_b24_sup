# 2026-08-17 11:05:20 - runtime config panel boundary

## Пользовательский промпт

`хорошо. бери в работу следующий крупный шаг`

## Что изменено

- в `spring-panel` добавлен internal read endpoint `GET /internal/api/bot/channels/{channelId}/runtime-config` для channel-scoped runtime snapshot;
- добавлен `BotRuntimeConfigService`, который собирает для runtime:
  - нормализованный `bot_settings`;
  - channel-scoped active template selection;
  - `locationTree`;
  - base `presetDefinitions`;
- `spring-panel` `SharedConfigService` расширен методом `presetDefinitions()`, а panel-side preset defaults вынесены в `BotSettingsDefaults`;
- в `java-bot/bot-core` добавлены `PanelRuntimeConfigClient` и `RuntimeConfigService` с rabbit-mode lookup через internal panel API и JDBC/shared fallback для legacy/dev режима;
- `BotSettingsService.loadFromChannel(...)` переведён на panel-owned runtime config в `rabbitmq`-маршруте;
- `bot-telegram`, `bot-vk` и `bot-max` перестали напрямую читать shared JSON для `locations`/`presetDefinitions` и теперь используют runtime-config snapshot через `RuntimeConfigService`.

## Проверка

- `cmd /c "mvnw.cmd -q -DskipTests compile"` в `spring-panel`
- `cmd /c "mvnw.cmd -q -Dtest=BotRuntimeReadApiControllerWebMvcTest,BotRuntimeConfigServiceTest,PanelBotSettingsServiceTest,FeedbackPromptDispatchSchedulerServiceTest test"` в `spring-panel`
- `cmd /c "mvnw.cmd -q -pl bot-core,bot-telegram,bot-vk,bot-max -DskipTests compile"` в `java-bot`
- `cmd /c "mvnw.cmd -q -pl bot-core -Dtest=BotSettingsServiceTest,ChannelServiceTest,EngagementTasksTest,OutboundFeedbackPromptDispatchServiceTest test"` в `java-bot`

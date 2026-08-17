# 2026-08-17 09:57:33 - channel-control-plane-boundary

## User prompt

`бери в работу следующий крупный шаг`

## Что изменено

- в `spring-panel` internal bot write API расширен двумя channel control-plane операциями:
  - `POST /internal/api/bot/channels/resolve`;
  - `PUT /internal/api/bot/channels/{channelId}/support-chat`;
- добавлен новый backend-owned сервис `BotRuntimeChannelService`, который:
  - резолвит configured channel по `channelId` и/или `token`;
  - создаёт запись канала при отсутствии;
  - назначает `publicId`;
  - обновляет `supportChatId`;
- `BotRuntimeWriteApiController` теперь умеет возвращать `ChannelResponse` с полями, достаточными для runtime-адаптеров `Telegram`, `VK` и `MAX`;
- в `java-bot/bot-core` добавлен `PanelChannelClient` для вызова internal panel API;
- `ChannelService` в `APP_INTEGRATION_TRANSPORT_MODE=rabbitmq` переведён на panel-owned boundary для:
  - `ensurePublicIdForToken(...)`;
  - `resolveConfiguredChannel(...)`;
  - `updateSupportChatId(...)`;
- legacy/JDBC fallback сохранён, чтобы локальный и совместимый режимы продолжали работать без panel API;
- добавлены targeted tests:
  - `spring-panel/src/test/java/com/example/panel/controller/BotRuntimeWriteApiControllerWebMvcTest.java`;
  - `spring-panel/src/test/java/com/example/panel/service/BotRuntimeChannelServiceTest.java`;
  - `java-bot/bot-core/src/test/java/com/example/supportbot/service/ChannelServiceTest.java`.

## Проверка

- `cmd /c "mvnw.cmd -q -pl bot-core,bot-telegram,bot-vk,bot-max -DskipTests compile"` в `java-bot`
- `cmd /c "mvnw.cmd -q -DskipTests compile"` в `spring-panel`
- `cmd /c "mvnw.cmd -q -pl bot-core -Dtest=ChannelServiceTest test"` в `java-bot`
- `cmd /c "mvnw.cmd -q -Dtest=BotRuntimeWriteApiControllerWebMvcTest,BotRuntimeChannelServiceTest test"` в `spring-panel`

## Что дальше

- следующий быстрый крупный шаг по `01-181` теперь логично брать в вынос оставшегося business/control-plane ownership из `bot/runtime` слоя:
  - `SharedConfigService` и runtime-config path;
  - `EngagementTasks` и связанные notification/settings сценарии;
  - другие legacy-boundary сервисы, которые ещё держат backend-ориентированный ownership в `java-bot`.

# 2026-04-23 14:49:00 — channels/runtime/autostart phase 6 expansion

## Что сделано
- Исправлен `ChannelApiController#createBotCredential`: пустой `platform`
  теперь нормализуется в `telegram`, а не проходит дальше как пустое значение.
- Расширен `ChannelApiControllerWebMvcTest`: добавлены сценарии на embedded
  credential summary в `/api/channels`, validation/normalization
  `bot_credentials` и `404` при удалении отсутствующего credential.
- Расширен `BotProcessApiControllerWebMvcTest`: добавлена stopped-state ветка
  для `/api/bots/{channelId}/status`.
- Расширен `BotRuntimeContractServiceTest`: добавлен `max`-contract сценарий
  и проверки optional `vk` env keys.
- Расширен `BotAutoStartServiceTest`: добавлена ветка автозапуска канала без
  привязки к credential.
- Синхронизированы `ARCHITECTURE_AUDIT_2026-04-08.md`,
  `ARCH_UI_REFACTORING_ROADMAP_2026-04-15.md` и `01-024.md`.

## Проверка
- `spring-panel\mvnw.cmd -q "-Dtest=ChannelApiControllerWebMvcTest,BotProcessApiControllerWebMvcTest,BotRuntimeContractServiceTest,BotAutoStartServiceTest" test`
- `spring-panel\mvnw.cmd -q -DskipTests compile`


# 2026-04-23 16:33:00 — panel-bot orchestration phase 6 error-contract pass

## Что сделано
- `BotProcessApiController` переведён на явный success/error contract для
  `start/stop/status`: success теперь зависит от реального runtime-status,
  а не всегда считается `true`.
- Расширен `BotProcessApiControllerWebMvcTest`:
  добавлены failure-ветки для `start/stop/status` и `max`-сценарий для
  `runtime-contract`.
- Расширен `BotAutoStartServiceTest`:
  добавлены ветки `null channel id` и
  `continue after failed start`.
- Расширен `ChannelApiControllerWebMvcTest`:
  добавлены ветки `test-message all failed` и
  `manual recipient only`.
- Синхронизированы `ARCHITECTURE_AUDIT_2026-04-08.md`,
  `ARCH_UI_REFACTORING_ROADMAP_2026-04-15.md` и `01-024.md`.

## Проверка
- `spring-panel\mvnw.cmd -q "-Dtest=BotProcessApiControllerWebMvcTest,BotAutoStartServiceTest,ChannelApiControllerWebMvcTest" test`
- `spring-panel\mvnw.cmd -q -DskipTests compile`


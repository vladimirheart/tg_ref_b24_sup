# 2026-04-16 08:50:20 - bot runtime launcher strategy phase 5 start

## Что сделано

- `BotProcessService` переведён с жёсткого `spring-boot:run` на launcher
  strategy: `app.bots.launch-mode=auto|jar|maven`;
- в режиме `auto` panel теперь предпочитает запуск готового executable `jar`
  для `bot-telegram`, `bot-vk`, `bot-max`, а `spring-boot:run` оставляет только
  как fallback для dev-сценария;
- добавлен `SettingsDialogConfigRoutingService` и дополнительная test safety net
  на routing/validation слои, чтобы продолжающийся рефакторинг не оставался без
  базовой проверки;
- добавлены тесты выбора launcher plan в `BotProcessServiceTest`.

## Проверка

- `spring-panel\mvnw.cmd -q -DskipTests compile`
- `spring-panel\mvnw.cmd -q "-Dtest=BotProcessServiceTest,SettingsDialogConfigRoutingServiceTest,SettingsDialogConfigSupportServiceTest" test`

## Эффект

- `Process And Runtime Boundary` roadmap перешёл из purely planned состояния в
  реальный код;
- panel стала менее зависимой от Maven runtime и лучше готова к запуску
  собранных bot-артефактов.

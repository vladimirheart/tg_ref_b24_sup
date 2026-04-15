# 2026-04-15 20:14:10 - dialog auth and settings macro governance pass

## Что сделано

- `DialogQuickActionsController` переведён на общий
  `DialogAuthorizationService`, чтобы controller больше не держал свои
  permission helper'ы и forbidden-audit;
- из `DialogApiController` убраны локальные dialog permission helper'ы, а
  remaining workspace permission flow переведён на `DialogAuthorizationService`;
- добавлен `SettingsMacroTemplateService`, который забрал macro template
  governance: publish rights, independent review, versioning, deprecation и
  workflow normalization;
- `SettingsBridgeController` переведён на делегирование macro template
  normalization в `SettingsMacroTemplateService`, за счёт чего сам controller
  перестал держать этот большой кусок доменной логики.

## Проверка

- `spring-panel\mvnw.cmd -q -DskipTests compile`

## Эффект

- dialog authorization теперь централизован и не дублируется в нескольких
  controller-слоях;
- `SettingsBridgeController` стал тоньше и потерял отдельный кусок macro
  governance-логики, который дальше можно развивать отдельно от bridge-layer.

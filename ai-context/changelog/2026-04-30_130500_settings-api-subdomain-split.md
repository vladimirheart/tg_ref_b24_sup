# 2026-04-30 13:05:00 — SettingsApiController subdomain split

## Что сделано

- `SettingsApiController` переведён на thin transport baseline:
  `client statuses` вынесены в `SettingsClientStatusService`,
  `it connection categories` — в `SettingsItConnectionCategoryService`,
  `integration network probe` — в `SettingsIntegrationNetworkProbeService`;
- поверх нового split добавлены targeted unit tests
  `SettingsClientStatusServiceTest`,
  `SettingsItConnectionCategoryServiceTest`,
  `SettingsIntegrationNetworkProbeServiceTest`
  и новый `SettingsApiControllerWebMvcTest`;
- `01-024`, roadmap и architecture audit синхронизированы под новый baseline:
  remaining `settings` риск теперь меньше в самом `SettingsApiController` и
  больше в соседних `catalog/reference/partner-network/bot-integration`
  governance tails.

## Проверка

- `.\mvnw.cmd -q "-Dtest=SettingsClientStatusServiceTest,SettingsItConnectionCategoryServiceTest,SettingsIntegrationNetworkProbeServiceTest,SettingsApiControllerWebMvcTest,SettingsBridgeControllerWebMvcTest,DialogWorkspaceRequestContractServiceTest,DialogWorkspacePayloadAssemblerServiceTest" test`
- `.\mvnw.cmd -q -DskipTests compile`

## Заметки

- `logs/spring-panel.log` обновился от локальных Maven-прогонов и вручную не
  редактировался.

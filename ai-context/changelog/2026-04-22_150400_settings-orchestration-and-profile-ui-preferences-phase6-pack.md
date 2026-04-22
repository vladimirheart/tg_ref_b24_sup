# 2026-04-22 15:04 — settings orchestration and profile ui-preferences phase 6 pack

## Что сделано

- добавлены targeted tests для orchestration-слоя настроек:
  `SettingsUpdateServiceTest` и `SettingsDialogConfigUpdateServiceTest`;
- добавлен WebMvc safety net для основного `/settings` update-route через
  `SettingsBridgeControllerWebMvcTest`;
- добавлен WebMvc safety net для server-backed UI preferences в
  `ProfileApiControllerWebMvcTest`;
- зафиксировано, что `settings` regression net теперь покрывает не только
  subdomain services, но и coordinator/API-слой.

## Проверка

- `spring-panel\mvnw.cmd -q "-Dtest=SettingsUpdateServiceTest,SettingsDialogConfigUpdateServiceTest,SettingsBridgeControllerWebMvcTest,ProfileApiControllerWebMvcTest,SettingsTopLevelUpdateServiceTest,SettingsLocationsUpdateServiceTest,UiPreferenceServiceTest,SettingsParameterServiceTest" test`
- `spring-panel\mvnw.cmd -q -DskipTests compile`

## Результат

- основной `/settings` coordinator flow и `dialog_config` coordinator теперь
  имеют прямую test-страховку;
- server-backed UI preferences прикрыты уже не только unit-тестами сервиса,
  но и WebMvc-контрактом профиля;
- roadmap, task-detail и architecture audit синхронизированы под новый объём
  `Phase 6`.

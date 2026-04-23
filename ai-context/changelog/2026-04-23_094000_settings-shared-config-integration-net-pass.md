# 2026-04-23 09:40:00 — settings shared-config integration net pass

## Что сделано

- добавлен round-trip test для `locations.json` в `SharedConfigServiceTest`;
- добавлен `SettingsParameterSharedConfigIntegrationTest` с проверкой реального
  sync `settings_parameters <-> locations.json`;
- добавлен `SettingsUpdateSharedConfigIntegrationTest` с проверкой, что
  `SettingsUpdateService` действительно пишет `settings.json` и `locations.json`
  через `SharedConfigService`;
- добавлена merge-регрессия в `UiPreferenceServiceTest`, подтверждающая, что
  обновление базовых UI prefs не затирает nested `dialogsTriage` payload;
- обновлены `ARCHITECTURE_AUDIT_2026-04-08.md`,
  `ARCH_UI_REFACTORING_ROADMAP_2026-04-15.md` и `01-024.md`.

## Проверка

- `mvnw.cmd -q "-Dtest=SharedConfigServiceTest,SettingsParameterSharedConfigIntegrationTest,SettingsUpdateSharedConfigIntegrationTest,UiPreferenceServiceTest" test`
- `mvnw.cmd -q -DskipTests compile`

## Зачем

Это расширяет `Phase 6` с targeted service/unit слоя до первых реальных
integration-сценариев на shared config boundary и снижает риск поломок вокруг
`settings.json`, `locations.json` и server-backed UI preferences.

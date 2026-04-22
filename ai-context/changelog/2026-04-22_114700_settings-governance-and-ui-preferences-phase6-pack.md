# 2026-04-22 11:47 — settings governance and ui-preferences phase 6 pack

## Что сделано

- добавлены targeted unit tests для `SettingsTopLevelUpdateService`,
  `SettingsLocationsUpdateService`, `SettingsParameterService` и
  `UiPreferenceService`;
- расширен `Phase 6` safety net вокруг `settings` за пределы уже вынесенных
  `dialog_config` subdomain services;
- исправлен alias-bug в `UiPreferenceService`: поля `sortMode`, `pageSize` и
  `updatedAtUtc` больше не теряются из-за premature default-normalization при
  сохранении `dialogsTriage` в server-backed UI preferences;
- актуализированы `01-024`, roadmap и архитектурный аудит под новый объём
  покрытия и bugfix.

## Проверка

- `spring-panel\mvnw.cmd -q "-Dtest=SettingsDialogRuntimeConfigServiceTest,SettingsDialogPublicFormConfigServiceTest,SettingsDialogSlaAiConfigServiceTest,SettingsDialogTemplateConfigServiceTest,SettingsDialogWorkspaceConfigServiceTest,SettingsTopLevelUpdateServiceTest,SettingsLocationsUpdateServiceTest,UiPreferenceServiceTest,SettingsParameterServiceTest" test`
- `spring-panel\mvnw.cmd -q -DskipTests compile`

## Результат

- `settings` governance/sync слой теперь лучше прикрыт перед следующими
  рефакторингами;
- server-backed UI preferences получили не только тесты, но и реальное
  исправление alias-нормализации в triage payload.

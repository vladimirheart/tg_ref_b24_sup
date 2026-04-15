# 2026-04-15 15:42:10 — triage controller and settings it-equipment split

## Что сделано

- добавлен `DialogTriagePreferencesController`, который теперь обслуживает
  `/api/dialogs/triage-preferences`;
- triage endpoints удалены из `DialogApiController`, при этом логика
  сохранения/telemetry сохранилась через уже выделенные `DialogTriagePreferenceService`
  и `DialogService`;
- добавлены `SettingsItEquipmentController` и `SettingsItEquipmentService` для
  `/api/settings/it-equipment`;
- CRUD по оборудованию, валидация полей и `notifyAllOperators` вынесены из
  `SettingsBridgeController` в отдельный service/controller слой.

## Зачем

Этот проход продолжает чистый controller split сразу в двух доменах:

- `DialogApiController` теряет ещё один endpoint-блок и становится ближе к
  разрезу по bounded contexts;
- `SettingsBridgeController` теряет ещё один самостоятельный API-модуль;
- структура `settings` начинает повторять тот же шаблон, что и `dialogs`:
  отдельный controller + отдельный service под конкретный slice.

## Проверка

- `spring-panel/mvnw.cmd -q -DskipTests compile`

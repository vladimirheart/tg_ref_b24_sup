# 2026-04-27 12:17:22 — dialogs/settings controller edge-cases phase 6 expansion

## Что сделано

- Расширен `dialogs` controller regression net:
  - `DialogQuickActionsControllerWebMvcTest`: domain error на `resolve`,
    null-body `categories`, invalid `snooze`, media failure;
  - `DialogReadControllerWebMvcTest`: `public-form-metrics`, details без
    `channelId`, default `offset=0`;
  - `DialogMacroControllerWebMvcTest`: alias `text` и variables без
    `ticketId`;
  - `DialogTriagePreferencesControllerWebMvcTest`: missing body и fallback
    `updated_at_utc`;
  - `DialogWorkspaceTelemetryControllerWebMvcTest`: null body и default
    `days=7`;
  - `DialogListControllerWebMvcTest`: empty dialogs payload.
- Расширен `settings` controller regression net:
  - `SettingsParametersControllerWebMvcTest`: trailing slash и `PATCH`
    contract;
  - `SettingsItEquipmentControllerWebMvcTest`: trailing slash и `PATCH`
    contract.

## Проверка

- `spring-panel\mvnw.cmd -q "-Dtest=DialogQuickActionsControllerWebMvcTest,DialogReadControllerWebMvcTest,SettingsParametersControllerWebMvcTest,SettingsItEquipmentControllerWebMvcTest,DialogMacroControllerWebMvcTest,DialogTriagePreferencesControllerWebMvcTest,DialogWorkspaceTelemetryControllerWebMvcTest,DialogListControllerWebMvcTest" test`
- `spring-panel\mvnw.cmd -q -DskipTests compile`

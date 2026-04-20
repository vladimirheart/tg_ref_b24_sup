# 2026-04-20 11:19:00 — Phase 6 WebMvc expansion and ownership doc pass

## Что сделано

- добавлены WebMvc tests для sliced dialog/settings controllers:
  `DialogReadController`, `DialogTriagePreferencesController`,
  `SettingsParametersController`, `SettingsItEquipmentController`,
  `DialogMacroController`, `DialogWorkspaceController`,
  `DialogQuickActionsController`;
- зафиксирован ownership UI preferences в `docs/UI_PREFERENCES_OWNERSHIP.md`;
- актуализирован `ARCH_UI_REFACTORING_ROADMAP_2026-04-15.md` под расширенное
  покрытие `Phase 6`;
- дополнены критерии готовности задачи `01-024` с учётом runtime contract docs,
  ownership docs и нового WebMvc safety net.

## Проверка

- `spring-panel\.\mvnw.cmd -q "-Dtest=DialogReadControllerWebMvcTest,DialogTriagePreferencesControllerWebMvcTest,SettingsParametersControllerWebMvcTest,SettingsItEquipmentControllerWebMvcTest,DialogMacroControllerWebMvcTest,DialogWorkspaceControllerWebMvcTest,DialogQuickActionsControllerWebMvcTest" test`

## Эффект

- `Phase 6` перестал покрывать только runtime/read/settings хвосты и получил
  адресную страховку для action/macro/workspace dialog API;
- документированный ownership UI preferences уменьшает риск повторного
  расползания browser/server/shared-config state;
- roadmap и task-detail теперь ближе к фактическому состоянию кода и тестов.

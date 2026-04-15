# 2026-04-15 14:50:59 — dialog triage preferences server-backed and sliced

## Что сделано

- расширен `UiPreferenceService`: теперь он сохраняет базовые UI preferences
  merge-способом, не затирая дополнительные пользовательские UI-срезы;
- в server-backed `ui_preferences.v1` добавлен вложенный блок
  `dialogsTriage` для operator triage preferences;
- создан `DialogTriagePreferenceService`, который инкапсулирует нормализацию,
  legacy fallback из `dialog_config.workspace_triage_preferences_by_operator`
  и сохранение triage prefs в новый storage, включая self-healing миграцию при
  первом чтении legacy-настроек;
- `DialogApiController` переведён на новый сервис, благодаря чему сценарий
  `/api/dialogs/triage-preferences` больше не пишет напрямую в shared JSON;
- обновлены task-detail и roadmap по фазам UI governance и dialog domain split.

## Зачем

Это продолжает разрезать смешанный слой UI/runtime-настроек:

- triage preferences перестают зависеть только от общего `settings.json`;
- `DialogApiController` теряет ещё один кусок хранения и нормализации;
- server-backed operator preferences получают расширяемую модель без потери уже
  сохранённых theme/sidebar-настроек.

## Проверка

- `spring-panel/mvnw.cmd -q -DskipTests compile`

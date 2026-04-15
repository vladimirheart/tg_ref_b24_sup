# 2026-04-15 14:30:47

## Заголовок

Третий этап 01-024: добавить server-backed bootstrap и sync для operator UI preferences

## Что изменено

- добавлен `UiPreferenceService` для загрузки и сохранения operator UI preferences через `settings_parameters`;
- `GlobalModelAttributes` теперь подмешивает bootstrap UI prefs и флаг sync-доступности в общий модельный слой;
- `ProfileApiController` расширен endpoint'ами `/profile/ui-preferences` для загрузки и сохранения prefs;
- `ui-head` теперь инлайнит bootstrap prefs до загрузки shared UI runtime;
- `ui-preferences.js` умеет гидрироваться из серверного bootstrap и синхронизировать изменения обратно на backend;
- текущий phase-2 roadmap и task detail обновлены с учётом server-backed слоя.

## Затронутые файлы

- `spring-panel/src/main/java/com/example/panel/service/UiPreferenceService.java`
- `spring-panel/src/main/java/com/example/panel/config/GlobalModelAttributes.java`
- `spring-panel/src/main/java/com/example/panel/controller/ProfileApiController.java`
- `spring-panel/src/main/resources/templates/fragments/ui-head.html`
- `spring-panel/src/main/resources/static/js/ui-preferences.js`
- `ai-context/tasks/task-details/01-024.md`
- `docs/ARCH_UI_REFACTORING_ROADMAP_2026-04-15.md`

## Проверка

- `spring-panel/.\\mvnw.cmd -q -DskipTests compile`

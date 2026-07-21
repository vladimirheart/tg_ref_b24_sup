# 2026-07-21 10:06:21 — settings bootstrap section-only contract

## Контекст
- Пользователь: `бери следующий шаг`
- Задача: `ai-context/tasks/task-details/01-150.md`

## Что сделано
- В `spring-panel/src/main/resources/static/js/settings-page-config-runtime.js` page-config builder переведён на чтение только секционного payload (`dialog/admin/channels/parameters/appearance/locations`) без normal-path fallback на top-level `...Initial`/legacy alias keys.
- В `spring-panel/src/main/resources/static/js/settings-page-bootstrap-runtime.js` runtime bootstrap больше не читает `options.dialogConfig`, `options.autoCloseConfig`, `options.autoCloseFallbackHours`, `options.cityOptions` и прочие top-level alias values как fallback-путь.
- В `spring-panel/src/main/resources/static/js/settings-channels-shell-runtime.js` и `spring-panel/src/main/resources/static/js/settings-admin-shell-runtime.js` удалены local fallback-ветки на `options.botSettingsInitial`, `options.integrationNetworkInitial`, `options.reportingConfigInitial`, `options.managerLocationBindingsInitial`.
- В `spring-panel/src/test/java/com/example/panel/controller/ManagementControllerWebMvcTest.java` добавлены и скорректированы проверки section-only bootstrap payload:
  - `/settings` не должен полагаться на `botSettingsInitial` / `reportingConfigInitial` / `locationsInitial` в init-payload;
  - canonical `question_templates` / `rating_templates` и imported template ids проверяются через bootstrap JSON contract, а не через plain-text HTML.
- В `ai-context/tasks/task-details/01-150.md` обновлены execution log, текущая точка остановки и следующий этап.

## Проверки
- `spring-panel\mvnw.cmd -Dtest=ManagementControllerWebMvcTest test`
- `spring-panel\mvnw.cmd -DskipTests compile`

## Следующий шаг
- Пройтись по remaining derived/deprecated shared settings mirrors (`question_flow`, `rating_system`, `auto_close_hours`) и определить, какие runtime/save-boundary read-fallback уже можно убирать без риска для старых конфигов.

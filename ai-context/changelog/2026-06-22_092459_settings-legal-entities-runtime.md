# 2026-06-22 09:24:59 - settings legal entities runtime

## Промпты пользователя

- `давай следующий шаг`

## Что изменено

- продолжен `01-129`: создан отдельный runtime `settings-legal-entities-runtime.js`
  для bounded subdomain `legal entities`;
- из `settings/index.html` убран inline runtime для `legal entities`: card render,
  draft state, save/delete/restore flow и input/change bindings теперь живут во
  внешнем JS-модуле;
- template переведён на явный mount/runtime API: добавлено подключение нового
  скрипта, `renderParameters()` делегирует рендер `legal entities` в runtime, а
  click/input/change hooks используют `settingsLegalEntitiesRuntime`.

## Затронутые файлы

- `spring-panel/src/main/resources/static/js/settings-legal-entities-runtime.js`
- `spring-panel/src/main/resources/templates/settings/index.html`
- `ai-context/tasks/task-details/01-129.md`

## Проверка

- `node --check spring-panel/src/main/resources/static/js/settings-legal-entities-runtime.js`
- `git diff --check -- spring-panel/src/main/resources/templates/settings/index.html spring-panel/src/main/resources/static/js/settings-legal-entities-runtime.js ai-context/tasks/task-details/01-129.md ai-context/changelog/2026-06-22_092459_settings-legal-entities-runtime.md`

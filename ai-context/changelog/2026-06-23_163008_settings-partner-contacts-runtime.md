# 2026-06-23 16:30:08 - settings partner contacts runtime

## Промпты пользователя

- `продолжи`

## Что изменено

- продолжен `01-129`: создан `settings-partner-contacts-runtime.js`, в который
  вынесен subdomain `partner contacts` вместе с normalizer-helpers, card render,
  draft/edit/save/delete/restore flow, editor modal и document-level
  click/input/change bindings;
- `settings/index.html` переведён на mount внешнего runtime для `partner contacts`:
  подключён новый script, `syncParameterData()` теперь берёт normalizer и сброс
  edit-state из runtime API, `renderParameters()` делегирует отрисовку во внешний
  модуль, а оставшиеся inline listeners и legacy partner-contact branches удалены;
- карточка `ai-context/tasks/task-details/01-129.md` обновлена под текущий статус:
  partner contacts зафиксированы как вынесенный пакет, а остаточный scope сужен
  в основном до `parameters` и shared helpers.

## Проверка

- `node --check spring-panel/src/main/resources/static/js/settings-partner-contacts-runtime.js`
- `git diff --check -- spring-panel/src/main/resources/templates/settings/index.html spring-panel/src/main/resources/static/js/settings-partner-contacts-runtime.js ai-context/tasks/task-details/01-129.md ai-context/changelog/2026-06-23_163008_settings-partner-contacts-runtime.md`

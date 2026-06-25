# 2026-06-25 12:04:18 - settings dialog templates runtime

## Промпты пользователя

- `давай следующий шаг`

## Что изменено

- продолжен `01-129`: создан `settings-dialog-templates-runtime.js`, который
  забрал subdomain `dialog templates / auto-close` вместе с category/question/
  completion/macro editors, auto-close cards, initial hydration и payload
  collection для сохранения настроек;
- `settings/index.html` переведён на mount нового dialog-template runtime:
  удалён большой inline-блок helper/renderer/callback функций для шаблонов и
  автозакрытия, а `saveSettings()` теперь получает `autoClose` и dialog-template
  payload через runtime API;
- карточка `01-129` обновлена: зафиксирован новый runtime-split пакет, а
  остаточный scope смещён на следующий слой dialog settings вокруг
  `time metrics / SLA / workspace governance / summary badges`.

## Проверка

- `node --check spring-panel/src/main/resources/static/js/settings-dialog-templates-runtime.js`
- `git diff --check -- spring-panel/src/main/resources/templates/settings/index.html spring-panel/src/main/resources/static/js/settings-dialog-templates-runtime.js ai-context/tasks/task-details/01-129.md ai-context/changelog/2026-06-25_120418_settings-dialog-templates-runtime.md`

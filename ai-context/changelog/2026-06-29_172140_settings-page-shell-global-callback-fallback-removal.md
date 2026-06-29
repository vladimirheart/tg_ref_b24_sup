# 2026-06-29 17:21:40 - settings page shell global callback fallback removal

## Промпты пользователя

- `давай следующий более по задаче`

## Что изменено

- `spring-panel/src/main/resources/static/js/settings-page-shell.js` больше не
  падает назад на `window[callbackName]` при резолве settings callback'ов;
  declarative/action/lifecycle/bootstrap слой теперь использует только
  `SettingsPageCallbackRegistry` и `Settings*Runtime` namespace contracts;
- перед удалением fallback дополнительно сверено покрытие callback-имён shell:
  текущие default callback names полностью покрыты registry/runtime map, без
  живых непокрытых имён;
- `ai-context/tasks/task-details/01-129.md` обновлён: в задаче зафиксировано,
  что page shell уже очищен от plain-global callback resolution, а remaining
  scope сместился к верхним helper/init boundary и дальнейшему cross-runtime
  cleanup.

## Проверка

- `node --check spring-panel/src/main/resources/static/js/settings-page-shell.js`
- `node -e \"...\"` — ad hoc проверка, что default callback names shell полностью покрыты registry/runtime map
- `git diff --check -- spring-panel/src/main/resources/static/js/settings-page-shell.js ai-context/tasks/task-details/01-129.md ai-context/changelog/2026-06-29_172140_settings-page-shell-global-callback-fallback-removal.md`

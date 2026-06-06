# 2026-06-06 10:30:03 - settings page shell parent child suspend

## Промпты пользователя

- `давай дальше`

## Что изменено

- продолжен `01-128`: generic parent/child modal suspend вынесен в `settings-page-shell.js`
  через декларативные hooks `data-settings-suspend-parent` и `data-settings-suspend-class`;
- `partnerContactEditorModal` и `channelEditorModal` в `settings/index.html` теперь описывают
  parent suspend behaviour через `data-*`, а не через локальные helper-функции
  `setParametersModalSuspended` и `setChannelsModalSuspended`;
- из giant inline script страницы настроек удалены дублирующиеся shell-helper'ы и прямые
  `show/hidden` вызовы для parent suspend/resume, при этом доменные cleanup-действия модалок
  оставлены на месте.

## Проверка

- `C:\Program Files\nodejs\node.exe --check spring-panel/src/main/resources/static/js/settings-page-shell.js`
- `git diff --check -- spring-panel/src/main/resources/templates/settings/index.html spring-panel/src/main/resources/static/js/settings-page-shell.js ai-context/changelog/2026-06-06_103003_settings-page-shell-parent-child-suspend.md`

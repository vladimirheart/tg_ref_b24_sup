# 2026-06-15 17:56:29 - settings page shell parent child registry

## Промпты пользователя

- `давай ещё один небольшой пакет`

## Что изменено

- в `spring-panel/src/main/resources/static/js/settings-page-shell.js` добавлен `DEFAULT_SETTINGS_PARENT_CHILD_RELATIONSHIPS` с runtime-конфигом parent/child modal-связок для settings shell;
- `initParentChildSuspendShell()` теперь разрешает parent modal и suspend class через runtime-регистр по `modal.id`, а `data-settings-suspend-parent` и `data-settings-suspend-class` остаются только как override-механизм;
- из `spring-panel/src/main/resources/templates/settings/index.html` убраны `data-settings-suspend-parent` и `data-settings-suspend-class` у child-модалок locations, parameters, IT, channels и VK webhook;
- блоки `publicform` и `questions_cfg` не затрагивались.

## Проверка

- `rg -n "data-settings-suspend-parent|data-settings-suspend-class" spring-panel/src/main/resources/templates/settings/index.html`
- `node --check spring-panel/src/main/resources/static/js/settings-page-shell.js`
- `Select-String -Path 'spring-panel/src/main/resources/static/js/settings-page-shell.js' -Pattern 'DEFAULT_SETTINGS_PARENT_CHILD_RELATIONSHIPS|resolveSettingsParentChildRelationship|initParentChildSuspendShell'`
- `git diff --check -- spring-panel/src/main/resources/static/js/settings-page-shell.js spring-panel/src/main/resources/templates/settings/index.html`

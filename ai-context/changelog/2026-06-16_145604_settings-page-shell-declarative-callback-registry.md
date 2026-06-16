# 2026-06-16 14:56:04 - settings page shell declarative callback registry

## Промпты пользователя

- `давай дальше`

## Что изменено

- в `spring-panel/src/main/resources/static/js/settings-page-shell.js` добавлен `DEFAULT_SETTINGS_DECLARATIVE_CALLBACKS` для `click`, `change` и `input`-callback trigger-ов;
- shell runtime теперь умеет разрешать declarative callbacks через registry selectors, а также поддерживает аргументы из `dataset` через `$dataset:<key>` и `$datasetNumber:<key>`;
- из `spring-panel/src/main/resources/templates/settings/index.html` убраны `data-settings-click-callback`, `data-settings-change-callback`, `data-settings-input-callback` и `data-settings-callback-args` для static settings controls, dynamic `locations` source editor и dialog/auto-close template editor controls;
- template переведён на доменные attrs вроде `data-settings-save-trigger`, `data-locations-source-field`, `data-category-row-add`, `data-macro-tag-row-remove` и `data-auto-close-template-remove`;
- блоки `publicform` и `questions_cfg` не затрагивались.

## Проверка

- `rg -n "data-settings-(click|change|input)-callback|data-settings-callback-args" spring-panel/src/main/resources/templates/settings/index.html`
- `Select-String -Path 'spring-panel/src/main/resources/static/js/settings-page-shell.js' -Pattern 'DEFAULT_SETTINGS_DECLARATIVE_CALLBACKS|resolveDefaultSettingsDeclarativeCallbackConfig|findSettingsDeclarativeCallbackTrigger|\$datasetNumber:'`
- `node --check spring-panel/src/main/resources/static/js/settings-page-shell.js`
- `git diff --check -- spring-panel/src/main/resources/static/js/settings-page-shell.js spring-panel/src/main/resources/templates/settings/index.html`

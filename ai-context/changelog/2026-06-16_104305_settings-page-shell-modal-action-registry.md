# 2026-06-16 10:43:05 - settings page shell modal action registry

## Промпты пользователя

- `давай дальше но расширенным пакетом`

## Что изменено

- в `spring-panel/src/main/resources/static/js/settings-page-shell.js` добавлен `DEFAULT_SETTINGS_MODAL_ACTIONS` и runtime-resolver для modal-action триггеров без обязательных `data-settings-open-modal` / `data-settings-action-callback` в markup;
- `initSettingsModalActionTriggers()`, `resolveSettingsModalActionTarget()` и `invokeSettingsActionCallback()` теперь умеют находить action trigger через runtime-регистр по доменным селекторам;
- из `spring-panel/src/main/resources/templates/settings/index.html` и из динамически генерируемой разметки убраны `data-settings-open-modal` и `data-settings-action-callback` для partner contacts, parameter cards, network profiles, integration network profiles, channel editor open и VK webhook open;
- в template оставлены только ещё не переведённые modal-action hooks, а блоки `publicform` и `questions_cfg` не затрагивались.

## Проверка

- `rg -n "data-settings-action-callback|data-settings-open-modal" spring-panel/src/main/resources/templates/settings/index.html`
- `Select-String -Path 'spring-panel/src/main/resources/static/js/settings-page-shell.js' -Pattern 'DEFAULT_SETTINGS_MODAL_ACTIONS|resolveDefaultSettingsModalActionConfig|findSettingsModalActionTrigger'`
- `node --check spring-panel/src/main/resources/static/js/settings-page-shell.js`
- `git diff --check -- spring-panel/src/main/resources/static/js/settings-page-shell.js spring-panel/src/main/resources/templates/settings/index.html`

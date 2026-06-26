# 2026-06-26 15:29:16 - settings page config runtime

## Промпты пользователя

- `давай следующий шаг по задаче 01-129`

## Что изменено

- добавлен `spring-panel/src/main/resources/static/js/settings-page-config-runtime.js`
  как слой статических defaults и нормализации root config для settings page;
- в новый runtime вынесены `DEFAULT_DIALOG_*`, `DEFAULT_SUMMARY_BADGES`,
  parameter enums/config maps, partner-contact mappings и helper нормализации
  server-injected page config;
- `spring-panel/src/main/resources/templates/settings/index.html` радикально
  упрощён: длинные literal-блоки defaults/config убраны, template теперь строит
  `settingsPageConfig` через `SettingsPageConfigRuntime.build(...)` и передаёт
  его в `SettingsPageBootstrapRuntime.mount(...)`;
- в template сохранены только необходимые compatibility globals
  `BOT_SETTINGS_INITIAL`, `INTEGRATION_NETWORK_*` и `BOT_PRESET_DEFINITIONS`
  для старого `bot-settings.js`;
- карточка `ai-context/tasks/task-details/01-129.md` обновлена под новый
  remaining scope после выноса root config/default layer.

## Проверка

- `node --check spring-panel/src/main/resources/static/js/settings-page-config-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-page-bootstrap-runtime.js`
- `node --check %TEMP%/settings-index-inline-check.js`
- `git diff --check` (`CRLF` warnings only)

# 2026-06-26 15:42:31 - settings page config legacy bot globals

## Промпты пользователя

- `давай следующий шаг по задаче 01-129`

## Что изменено

- `spring-panel/src/main/resources/static/js/settings-page-config-runtime.js`
  расширен публикацией legacy `BOT_*` globals через
  `publishLegacyGlobals(settingsPageConfig)`;
- `spring-panel/src/main/resources/templates/settings/index.html` ещё упрощён:
  удалены локальные compatibility-константы `BOT_SETTINGS_INITIAL`,
  `INTEGRATION_NETWORK_INITIAL`, `INTEGRATION_NETWORK_PROFILES_INITIAL` и
  `BOT_PRESET_DEFINITIONS`;
- template теперь только собирает `settingsPageConfig` из raw Thymeleaf payload,
  публикует legacy bot globals через runtime и передаёт config в
  `SettingsPageBootstrapRuntime.mount(...)`;
- карточка `ai-context/tasks/task-details/01-129.md` обновлена: remaining scope
  ещё уже и больше не включает локальные bot compatibility-константы в template.

## Проверка

- `node --check spring-panel/src/main/resources/static/js/settings-page-config-runtime.js`
- `node --check %TEMP%/settings-index-inline-check.js`
- `git diff --check` (`CRLF` warnings only)

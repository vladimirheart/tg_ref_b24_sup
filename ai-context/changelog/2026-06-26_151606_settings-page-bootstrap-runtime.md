# 2026-06-26 15:16:06 - settings page bootstrap runtime

## Промпты пользователя

- `давай следующий шаг по задаче 01-129`

## Что изменено

- добавлен `spring-panel/src/main/resources/static/js/settings-page-bootstrap-runtime.js`
  как общий bootstrap runtime для settings page;
- в новый runtime перенесена почти вся оставшаяся page-level mount-композиция:
  `dialog / parameters / network profiles / appearance / locations / channels / admin`;
- из `spring-panel/src/main/resources/templates/settings/index.html` убраны
  локальный `escapeHtml`, inline `saveSettings()` и длинная цепочка ручных
  runtime `mount(...)`; template теперь сводится к одному
  `SettingsPageBootstrapRuntime.mount(...)` с server-injected payload/defaults;
- внутри bootstrap runtime заодно выправлен порядок инициализации вокруг
  `settingsSaveRuntime` и `settingsNetworkProfilesRuntime`, чтобы save-layer
  получал уже смонтированный network profiles runtime;
- карточка `ai-context/tasks/task-details/01-129.md` обновлена под новый
  remaining scope после выноса bootstrap orchestration.

## Проверка

- `node --check spring-panel/src/main/resources/static/js/settings-page-bootstrap-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-admin-shell-runtime.js`
- `node --check %TEMP%/settings-index-inline-check.js`
- `git diff --check` (`CRLF` warnings only)

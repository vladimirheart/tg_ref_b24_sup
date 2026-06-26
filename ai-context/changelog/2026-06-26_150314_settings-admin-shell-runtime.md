# 2026-06-26 15:03:14 - settings admin shell runtime

## Промпты пользователя

- `давай следующий шаг по задаче 01-129`

## Что изменено

- добавлен `spring-panel/src/main/resources/static/js/settings-admin-shell-runtime.js`
  как page-level shell для admin-хвоста настроек;
- в новый runtime перенесены global bridge callbacks
  `mountAuthManagementSettingsModal` и `resetAuthManagementSettingsModal` для
  lifecycle модалки управления доступом;
- `settings-reporting-manager-bindings.js` теперь монтируется через
  `SettingsAdminShellRuntime`, а не напрямую inline в
  `spring-panel/src/main/resources/templates/settings/index.html`;
- карточка `ai-context/tasks/task-details/01-129.md` обновлена: зафиксирован
  новый remaining scope после выноса `auth management / reporting` bridge-хвоста.

## Проверка

- `node --check spring-panel/src/main/resources/static/js/settings-admin-shell-runtime.js`
- `node --check %TEMP%/settings-index-inline-check.js`
- `git diff --check` (`CRLF` warnings only)

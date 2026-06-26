# 2026-06-26 16:00:25 - settings page init json payload

## Промпты пользователя

- `давай следующий шаг по задаче 01-129`

## Что изменено

- `spring-panel/src/main/resources/static/js/settings-page-init-runtime.js`
  расширен auto-mount логикой для `script[data-settings-page-init-payload]` и
  JSON payload parsing;
- `spring-panel/src/main/resources/templates/settings/index.html` больше не
  содержит исполняемый init-скрипт для settings page: он заменён на
  `application/json` payload block с `th:inline="javascript"`;
- связка `SettingsPageConfigRuntime -> SettingsPageInitRuntime ->
  SettingsPageBootstrapRuntime` теперь запускается автоматически через payload
  script без ручного inline `mount(...)`;
- карточка `ai-context/tasks/task-details/01-129.md` обновлена: remaining scope
  по template смещён уже к самому server-rendered payload contract.

## Проверка

- `node --check spring-panel/src/main/resources/static/js/settings-page-init-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-page-config-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-page-bootstrap-runtime.js`
- `node -e` с `JSON.parse(...)` для payload script после безопасной замены Thymeleaf placeholders
- `git diff --check` (`CRLF` warnings only)

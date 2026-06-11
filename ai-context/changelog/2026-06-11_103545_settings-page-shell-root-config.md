# 2026-06-11 10:35:45 - settings page shell root config

## Промпты пользователя

- `давай дальше`
- `давай дальше`

## Что изменено

- продолжен `01-128`: в `settings-page-shell.js` добавлен общий helper `getSettingsShellRoot()`,
  чтобы shell runtime меньше зависел от повторяющихся прямых `querySelector` вызовов;
- root `data-settings-page-shell` в `settings/index.html` теперь хранит не только
  `data-settings-bootstrap`, но и явные URL-параметры `data-settings-url-open-param`
  и `data-settings-url-legacy-param`;
- `openRequestedSettingsModalFromUrl()` переведён на чтение URL-конфига из root markup
  через новый helper `readSettingsUrlRequest(params)`, вместо жёстко пришитых `open/tab`
  прямо в коде shell;
- `runSettingsDomainBootstrap()` тоже переведён на использование `getSettingsShellRoot()`,
  чтобы весь root-driven orchestration слой шёл через один вход в shell runtime.

## Проверка

- `C:\Program Files\nodejs\node.exe --check spring-panel/src/main/resources/static/js/settings-page-shell.js`
- `git diff --check -- spring-panel/src/main/resources/templates/settings/index.html spring-panel/src/main/resources/static/js/settings-page-shell.js ai-context/changelog/2026-06-11_103545_settings-page-shell-root-config.md`

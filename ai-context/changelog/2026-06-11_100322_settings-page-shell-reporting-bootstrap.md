# 2026-06-11 10:03:22 - settings page shell reporting bootstrap

## Промпты пользователя

- `давай дальше`
- `продолжай`
- `продолжай`

## Что изменено

- продолжен `01-128`: `initReporting` включён в общий декларативный список
  `data-settings-bootstrap` на корневом `data-settings-page-shell`;
- из `initSettingsPageShell()` в `settings-page-shell.js` удалён последний special-case
  вызов доменной инициализации `initReporting()`;
- после этого domain bootstrap страницы настроек полностью проходит через
  `runSettingsDomainBootstrap()` и markup-driven bootstrap list, без отдельных hardcoded
  точек запуска в main shell entrypoint.

## Проверка

- `C:\Program Files\nodejs\node.exe --check spring-panel/src/main/resources/static/js/settings-page-shell.js`
- `git diff --check -- spring-panel/src/main/resources/templates/settings/index.html spring-panel/src/main/resources/static/js/settings-page-shell.js ai-context/changelog/2026-06-11_100322_settings-page-shell-reporting-bootstrap.md`

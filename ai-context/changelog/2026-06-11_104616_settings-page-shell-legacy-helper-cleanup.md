# 2026-06-11 10:46:16 - settings page shell legacy helper cleanup

## Промпты пользователя

- `хорошо, продолжай`
- `давай дальше`
- `продолжи`

## Что изменено

- продолжен `01-128`: из `settings-page-shell.js` удалены больше не используемые legacy-helper'ы
  `initAuthManagementModalShell()` и `initLocationsModalShell()`;
- эти helper'ы стали мёртвым кодом после перевода `usersModal` и `locationsModal`
  на декларативные `data-settings-on-shown/hidden` hooks и template-level callback entrypoint'ы;
- shell runtime теперь чище отражает реальный bootstrap-поток страницы настроек и не держит
  внутри себя старые imperative-сценарии, которые больше не вызываются.

## Проверка

- `C:\Program Files\nodejs\node.exe --check spring-panel/src/main/resources/static/js/settings-page-shell.js`
- `git diff --check -- spring-panel/src/main/resources/static/js/settings-page-shell.js ai-context/changelog/2026-06-11_104616_settings-page-shell-legacy-helper-cleanup.md`

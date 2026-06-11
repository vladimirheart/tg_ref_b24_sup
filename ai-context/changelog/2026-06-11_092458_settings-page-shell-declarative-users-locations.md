# 2026-06-11 09:24:58 - settings page shell declarative users locations

## Промпты пользователя

- `давай дальше`
- `продолжи`
- `продолжи`
- `продолжи`

## Что изменено

- продолжен `01-128`: `usersModal` и `locationsModal` переведены на декларативные
  `data-settings-on-shown` / `data-settings-on-hidden` hooks вместо жёсткой привязки
  shell runtime к конкретным modal id;
- в `settings/index.html` добавлены тонкие `window.*` callback entrypoint'ы
  `mountAuthManagementSettingsModal`, `resetAuthManagementSettingsModal`,
  `prepareLocationsSettingsModal` и `resetLocationsSettingsModal`;
- lifecycle `AuthManagement` mount/refresh/reset и lifecycle `locations sync`
  render/polling теперь запускаются через общий dispatcher `settings-page-shell.js`,
  а не через прямой imperative bootstrap внутри shell entrypoint;
- из `initSettingsPageShell()` убраны реальные вызовы `initAuthManagementModalShell()`
  и `initLocationsModalShell()`, так что page-shell больше не зависит от этих
  жёстко пришитых шагов в runtime-потоке.

## Проверка

- `C:\Program Files\nodejs\node.exe --check spring-panel/src/main/resources/static/js/settings-page-shell.js`
- `git diff --check -- spring-panel/src/main/resources/templates/settings/index.html spring-panel/src/main/resources/static/js/settings-page-shell.js ai-context/changelog/2026-06-11_092458_settings-page-shell-declarative-users-locations.md`

# 2026-06-08 12:33:55 - settings page shell modal api

## Промпты пользователя

- `давай дальше`

## Что изменено

- продолжен `01-128`: `settings-page-shell.js` теперь подключается до giant inline script,
  рядом с `bootstrap.bundle`, чтобы template мог использовать shell API уже во время
  инициализации page runtime;
- в `settings-page-shell.js` добавлен явный `window.SettingsPageShell` API с методами
  `getModalInstance`, `showModal` и `hideModal`, инкапсулирующий работу с `bootstrap.Modal`;
- `settings/index.html` переведён с прямых `new bootstrap.Modal(...)`,
  `bootstrap.Modal.getOrCreateInstance(...)` и `bootstrap.Modal.getInstance(...)`
  на вызовы `window.SettingsPageShell.*` для partner contact editor, parameter items,
  network profile editor, it add modals, location wizard, integration network profile,
  channel editor, add channel и VK webhook modal;
- из нижнего хвоста `settings/index.html` удалено позднее повторное подключение
  `settings-page-shell.js`, чтобы shell runtime жил в одном явном месте загрузки.

## Проверка

- `C:\Program Files\nodejs\node.exe --check spring-panel/src/main/resources/static/js/settings-page-shell.js`
- `git diff --check -- spring-panel/src/main/resources/templates/settings/index.html spring-panel/src/main/resources/static/js/settings-page-shell.js ai-context/changelog/2026-06-08_123355_settings-page-shell-modal-api.md`

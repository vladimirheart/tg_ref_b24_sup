# 2026-06-11 14:31:02 - settings page shell tile open runtime

## Промпты пользователя

- `давай дальше`

## Что изменено

- продолжен `01-128`: `settings-page-shell.js` теперь сам открывает settings-модалки по
  `data-settings-tile-target`, когда пользователь кликает overview-тайл или активирует его
  с клавиатуры;
- из overview-тайлов в `settings/index.html` убраны `data-bs-toggle=\"modal\"` и
  `data-bs-target=\"#...\"`, чтобы этот слой больше не зависел от Bootstrap data-api;
- связь между settings overview и primary modal layer теперь идёт через собственный
  declarative contract страницы настроек: `data-settings-tile` + `data-settings-tile-target`.

## Проверка

- `C:\Program Files\nodejs\node.exe --check spring-panel/src/main/resources/static/js/settings-page-shell.js`
- `git diff --check -- spring-panel/src/main/resources/templates/settings/index.html spring-panel/src/main/resources/static/js/settings-page-shell.js ai-context/changelog/2026-06-11_143102_settings-page-shell-tile-open-runtime.md`

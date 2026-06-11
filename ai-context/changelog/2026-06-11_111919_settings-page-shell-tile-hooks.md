# 2026-06-11 11:19:19 - settings page shell tile hooks

## Промпты пользователя

- `хорошо, продолжай`
- `продолжи`

## Что изменено

- продолжен `01-128`: overview-тайлы страницы настроек получили явные hooks
  `data-settings-tile` и `data-settings-tile-target` вместо неявной привязки shell runtime
  только к `data-bs-target`;
- `initSettingsTileDescriptions()` в `settings-page-shell.js` теперь инициализирует tile-flow
  через `data-settings-tile`, а `resolveSettingsTileForModal()` сначала ищет явный
  `data-settings-tile-target`, оставляя старый bootstrap-based путь только как fallback;
- за счёт этого shell меньше зависит от bootstrap-атрибутов как от единственного источника
  связи между settings overview и модалками, а markup становится ближе к собственному
  declarative contract страницы настроек.

## Проверка

- `C:\Program Files\nodejs\node.exe --check spring-panel/src/main/resources/static/js/settings-page-shell.js`
- `git diff --check -- spring-panel/src/main/resources/templates/settings/index.html spring-panel/src/main/resources/static/js/settings-page-shell.js ai-context/changelog/2026-06-11_111919_settings-page-shell-tile-hooks.md`

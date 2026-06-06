# 2026-06-06 10:16:24 - settings page shell domain bootstrap

## Промпты пользователя

- `продолжай`

## Что изменено

- продолжен `01-128`: из `settings/index.html` вынесен ещё один orchestration-слой,
  который раньше через `DOMContentLoaded` запускал готовые init-функции settings-поддоменов;
- в `settings-page-shell.js` добавлен единый page-bootstrap для вызова
  `initClientStatuses`, `initBusinessStylesEditor`, `initAutoCloseTemplates`,
  `initDialogTemplates`, `initLocationWizard`, `buildLocationsTree`,
  `initChannelsManagement`, `loadParameters`, `renderNetworkProfiles` и соседних init/render функций;
- lifecycle `locationsModal` для initial render и sync-polling переведён на внешний shell runtime,
  а в markup `locationsModal` добавлен явный hook `data-settings-locations-modal`;
- giant inline script страницы настроек стал ещё меньше и теперь держит меньше page-entry orchestration,
  оставаясь ближе к роли host для доменных функций.

## Проверка

- `C:\Program Files\nodejs\node.exe --check spring-panel/src/main/resources/static/js/settings-page-shell.js`
- `git diff --check -- spring-panel/src/main/resources/templates/settings/index.html spring-panel/src/main/resources/static/js/settings-page-shell.js ai-context/changelog/2026-06-06_101624_settings-page-shell-domain-bootstrap.md`

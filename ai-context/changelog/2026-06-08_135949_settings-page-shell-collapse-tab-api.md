# 2026-06-08 13:59:49 - settings page shell collapse tab api

## Промпты пользователя

- `давай дальше`

## Что изменено

- продолжен `01-128`: `settings-page-shell.js` расширен с modal API до общего shell UI API,
  включающего `getCollapseInstance`, `showCollapse`, `getTabInstance` и `showTab`;
- `settings-page-shell.js` теперь использует собственные helper'ы для query-driven modal open
  и reset стартовой вкладки вместо прямых `bootstrap.Modal` и `bootstrap.Tab` вызовов внутри shell;
- `settings/index.html` переведён с прямых `bootstrap.Collapse` вызовов на
  `window.SettingsPageShell.showCollapse(...)` в IT section tile-flow и channels manage collapse flow;
- в результате template ещё меньше знает о Bootstrap runtime напрямую, а инфраструктурный UI-layer
  страницы настроек концентрируется во внешнем `settings-page-shell.js`.

## Проверка

- `C:\Program Files\nodejs\node.exe --check spring-panel/src/main/resources/static/js/settings-page-shell.js`
- `git diff --check -- spring-panel/src/main/resources/templates/settings/index.html spring-panel/src/main/resources/static/js/settings-page-shell.js ai-context/changelog/2026-06-08_135949_settings-page-shell-collapse-tab-api.md`

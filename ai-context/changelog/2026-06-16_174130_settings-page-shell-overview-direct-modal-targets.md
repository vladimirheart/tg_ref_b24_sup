# 2026-06-16 17:41:30 - settings page shell overview direct modal targets

## Промпты пользователя

- `хорошо, продолжай`

## Что изменено

- в `spring-panel/src/main/resources/static/js/settings-page-shell.js` shell root переведён с `data-settings-page-shell` на стабильный контейнер `.settings-surface.page-shell--settings`;
- из `settings-page-shell.js` удалён registry `DEFAULT_SETTINGS_TILE_MODAL_TARGETS`, а overview-плитки теперь открывают модалки по прямому `data-settings-overview-target` без промежуточного key-to-modal маппинга;
- runtime-инициализация и поиск активной плитки переведены с `[data-settings-tile]` на `[data-settings-overview-target]`;
- в `spring-panel/src/main/resources/templates/settings/index.html` удалён `data-settings-page-shell`, а все overview-плитки настроек переведены с `data-settings-tile="<key>"` на явные `data-settings-overview-target="<modalId>"`;
- блоки `publicform` и `questions_cfg` не затрагивались.

## Проверка

- `rg -n "data-settings-page-shell|data-settings-tile" spring-panel/src/main/resources/templates/settings/index.html spring-panel/src/main/resources/static/js/settings-page-shell.js`
- `rg -n "data-settings-overview-target" spring-panel/src/main/resources/templates/settings/index.html spring-panel/src/main/resources/static/js/settings-page-shell.js`
- `node --check spring-panel/src/main/resources/static/js/settings-page-shell.js`
- `git diff --check -- spring-panel/src/main/resources/static/js/settings-page-shell.js spring-panel/src/main/resources/templates/settings/index.html`

# 2026-06-17 22:37:44 - settings appearance runtime split

## Промпты пользователя

- `давай следующий шаг`

## Что изменено

- добавлен новый bounded runtime-модуль `spring-panel/src/main/resources/static/js/settings-appearance-runtime.js` для subdomain `client statuses / business styles`;
- новый модуль забрал state, render/save flow и глобальные entry-point'ы `initClientStatuses`, `initBusinessStylesEditor`, `startStatusesEdit`, `saveClientStatuses`, `cancelStatusesEdit`, `addStatus`, которые нужны `settings-page-shell`;
- в `spring-panel/src/main/resources/templates/settings/index.html` подключён `settings-appearance-runtime.js` и добавлен тонкий `window.SettingsAppearanceRuntime?.mount(...)` с передачей initial payload для статусов и меток бизнесов;
- из inline runtime в `settings/index.html` удалён большой блок логики управления статусами клиентов и метками бизнесов;
- блоки `publicform` и `questions_cfg` не затрагивались.

## Проверка

- `node --check spring-panel/src/main/resources/static/js/settings-appearance-runtime.js`
- `rg -n "window\\.initClientStatuses|window\\.initBusinessStylesEditor|window\\.startStatusesEdit|window\\.saveClientStatuses|window\\.cancelStatusesEdit|window\\.addStatus|SettingsAppearanceRuntime|mount\\(" spring-panel/src/main/resources/static/js/settings-appearance-runtime.js spring-panel/src/main/resources/templates/settings/index.html`
- `rg -n "STATUS_FILTER_BASE_URL|CLIENT_STATUS_COLORS|statusEditMode|initialStatusesSnapshot|businessStylesState|saveBusinessStyles|collectBusinessStylesPayload|sanitizeStatusColorMap|buildStatusRow|renderStatusesList|initClientStatuses\\(|initBusinessStylesEditor\\(|startStatusesEdit\\(|cancelStatusesEdit\\(|addStatus\\(|saveClientStatuses\\(" spring-panel/src/main/resources/templates/settings/index.html`
- `git diff --check -- spring-panel/src/main/resources/static/js/settings-appearance-runtime.js spring-panel/src/main/resources/templates/settings/index.html`

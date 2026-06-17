# 2026-06-17 22:28:37 - settings reporting manager bindings runtime split

## Промпты пользователя

- `выполни следующий логичный шаг`
- `продолжай`

## Что изменено

- добавлен новый bounded runtime-модуль `spring-panel/src/main/resources/static/js/settings-reporting-manager-bindings.js`, который забрал на себя subdomain `reporting / manager bindings`;
- новый модуль монтирует `initReporting`, `prepareReportingSettingsModal` и `prepareManagerBindingsSettingsModal`, а также держит собственные state, DOM-binding и save/load flow для автоотчётов и привязок управляющих;
- в `spring-panel/src/main/resources/templates/settings/index.html` подключён новый script-файл и вместо большого inline-блока оставлен только тонкий `window.SettingsReportingManagerBindings?.mount(...)` с передачей initial payload и доступа к `locationsState`;
- из inline runtime в `settings/index.html` убраны локальные `reportingState`, `managerBindingsState`, `reportingEls`, `managerBindingsEls` и связанные helper-функции этого subdomain;
- в `ai-context/tasks/task-list.md` задача `01-129` переведена в статус `🟡`, так как client-side split уже начат реальным модульным пакетом;
- блоки `publicform` и `questions_cfg` не затрагивались.

## Проверка

- `node --check spring-panel/src/main/resources/static/js/settings-reporting-manager-bindings.js`
- `rg -n "settings-reporting-manager-bindings|SettingsReportingManagerBindings|prepareReportingSettingsModal|prepareManagerBindingsSettingsModal|initReporting" spring-panel/src/main/resources/templates/settings/index.html spring-panel/src/main/resources/static/js/settings-reporting-manager-bindings.js`
- `rg -n "reportingState|managerBindingsState|reportingEls|managerBindingsEls|safeEscapeHtml|getLocationBindingOptionsFromTree|getAuthManagerInstance|extractOrgPeopleOptions|normalizeReportingConfig\(" spring-panel/src/main/resources/templates/settings/index.html`
- `git diff --check -- spring-panel/src/main/resources/static/js/settings-reporting-manager-bindings.js spring-panel/src/main/resources/templates/settings/index.html ai-context/tasks/task-list.md`

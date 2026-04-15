# 2026-04-15 14:18:49

## Заголовок

Второй этап 01-024: ввести общий runtime для browser-only UI preferences

## Что изменено

- добавлен общий модуль `ui-preferences.js` с реестром browser-only UI preferences;
- в `ui-head` он подключается раньше `theme.js`, чтобы shared UI runtime и тема
  использовали единый источник состояния;
- `theme.js` переведён на новый preferences runtime для `theme` и
  `themePalette`, с сохранением fallback-режима;
- `sidebar.js` переведён на новый preferences runtime для `sidebarPinned`,
  `uiDensityMode` и `sidebarNavOrder`;
- добавлена event-driven синхронизация через `ui-preference:change`, чтобы
  shared-скрипты меньше зависели от прямых чтений `localStorage`.

## Затронутые файлы

- `spring-panel/src/main/resources/static/js/ui-preferences.js`
- `spring-panel/src/main/resources/static/js/theme.js`
- `spring-panel/src/main/resources/static/js/sidebar.js`
- `spring-panel/src/main/resources/templates/fragments/ui-head.html`
- `ai-context/tasks/task-details/01-024.md`
- `docs/ARCH_UI_REFACTORING_ROADMAP_2026-04-15.md`

## Проверка

- `spring-panel/.\\mvnw.cmd -q -DskipTests compile`

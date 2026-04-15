# 2026-04-15 09:53:06 - общий ui-config и presets для основных страниц

## Что изменено

- Добавлен общий клиентский слой `ui-config.js` с presets для `dashboard`,
  `settings` и `dialogs`.
- В `style.css` добавлены базовые UI-переменные для shell/panel слоя и
  page-specific presets через `data-ui-page` и `data-ui-density`.
- В `app.css` добавлены shared-паттерны `page-shell`, `page-hero`, `ui-panel`,
  `control-panel`, `data-panel` и общие section-helpers.
- `dashboard`, `settings` и `dialogs` подключены к `ui-config.js`, получили
  `data-ui-page` и начали использовать shared shell/panel-классы.
- Создана основа для дальнейшего перевода page-specific UI в единый preset
  подход без смешивания темы, layout и компонентных стилей.

## Проверка

- `spring-panel\\mvnw.cmd -q -DskipTests compile`

## Затронутые файлы

- `ai-context/tasks/task-list.md`
- `ai-context/tasks/task-details/01-022.md`
- `ai-context/changelog/2026-04-15_095306_ui-config-presets-foundation-for-main-pages.md`
- `spring-panel/src/main/resources/static/js/ui-config.js`
- `spring-panel/src/main/resources/static/css/style.css`
- `spring-panel/src/main/resources/static/css/app.css`
- `spring-panel/src/main/resources/templates/dashboard/index.html`
- `spring-panel/src/main/resources/templates/settings/index.html`
- `spring-panel/src/main/resources/templates/dialogs/index.html`

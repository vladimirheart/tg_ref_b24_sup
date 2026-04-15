# 2026-04-15 14:10:14

## Заголовок

Первый этап архитектурного рефакторинга: централизовать head-bootstrap UI и зафиксировать roadmap

## Что изменено

- добавлен общий fragment `fragments/ui-head.html` для раннего подключения `theme.js` и `ui-config.js`;
- тема больше не подключается поздно через `navbar`, а применяется из `<head>` на всех страницах с базовым UI-слоем;
- `ui-config` расширен на дополнительные разделы панели: `analytics`, `clients`, `knowledge`, `channels`, `users`, `tasks`, `passports`, `public`, `ai-ops`;
- в `style.css` добавлены базовые page presets по ширине и shell-параметрам для новых `data-ui-page`;
- шаблоны панели переведены на общий `ui-head` fragment вместо разрозненного подключения `ui-config`;
- добавлен roadmap-файл по архитектурному и UI-рефакторингу.

## Затронутые файлы

- `spring-panel/src/main/resources/templates/fragments/ui-head.html`
- `spring-panel/src/main/resources/templates/fragments/navbar.html`
- `spring-panel/src/main/resources/static/js/ui-config.js`
- `spring-panel/src/main/resources/static/css/style.css`
- `spring-panel/src/main/resources/templates/**/*.html`
- `docs/ARCH_UI_REFACTORING_ROADMAP_2026-04-15.md`
- `ai-context/tasks/task-list.md`
- `ai-context/tasks/task-details/01-024.md`

## Проверка

- `spring-panel/.\\mvnw.cmd -q -DskipTests compile`

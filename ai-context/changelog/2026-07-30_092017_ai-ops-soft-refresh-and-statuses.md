# AI Ops soft refresh and statuses

## Промты пользователя

- `остались-ли ещё какие-то доработки?`
- `делай и сразу приступай к выполнению`

## Что изменено

- В `spring-panel/src/main/resources/templates/dialogs/ai-ops.html` добавлена верхняя UX-панель со статусом операторских действий и индикатором автообновления.
- В `spring-panel/src/main/resources/static/js/ai-ops.js` добавлен мягкий polling `review queue` и `offline eval` с интервалом 20 секунд и повторной синхронизацией при возврате на вкладку.
- В `spring-panel/src/main/resources/static/js/ai-ops.js` рендер секций переведён на `setTextIfChanged`/`setHtmlIfChanged`, чтобы не перерисовывать DOM при неизменившихся данных и убрать визуальное дёрганье.
- В `spring-panel/src/main/resources/static/js/ai-ops.js` добавлены явные success/error статусы после `approve`, `reject`, ручных refresh-действий и запуска `offline eval`.
- В `ai-context/tasks/task-list.md` и `ai-context/tasks/task-details/01-155.md` зафиксирована и закрыта текущая добивка по task-flow проекта.

## Проверки

- `node --check spring-panel/src/main/resources/static/js/ai-ops.js`
- `./mvnw.cmd -q -DskipTests compile`
- `./mvnw.cmd -q "-Dtest=DialogsControllerWebMvcTest,DialogAiOpsControllerWebMvcTest" test`

## Примечания

- Изменения не затрагивают backend API и сосредоточены на page-level UX/refresh orchestration страницы `AI Ops`.
- Внешние runtime-файлы, БД и логи, изменённые вне задачи, не трогались.

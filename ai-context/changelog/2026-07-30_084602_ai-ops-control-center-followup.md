# AI Ops control center follow-up

## Промт пользователя

- `в проекте есть "AI-Ops" но он что-то не желает работать как запланированно`

## Что изменено

- В `spring-panel/src/main/resources/templates/dialogs/ai-ops.html` добавлены отдельные секции `AI Review Queue` и `AI Offline Eval`, чтобы standalone-страница `AI Ops` показывала не только мониторинг и solution memory, но и рабочие operator-facing сценарии.
- В `spring-panel/src/main/resources/static/js/ai-ops.js` добавлена загрузка очереди ревизий через `/api/dialogs/ai-reviews`, действия approve/reject, переход в связанный диалог и синхронизация списка после действий.
- В `spring-panel/src/main/resources/static/js/ai-ops.js` добавлена загрузка offline eval summary через `/api/dialogs/ai-monitoring/offline-eval` и ручной запуск через `/api/dialogs/ai-monitoring/offline-eval/run` с обновлением метрик и примеров ошибок.
- В `ai-context/tasks/task-list.md` и `ai-context/tasks/task-details/01-154.md` зафиксирована задача и её текущее состояние по task-flow проекта.

## Проверки

- `node --check spring-panel/src/main/resources/static/js/ai-ops.js`
- `./mvnw.cmd -q -DskipTests compile`
- `./mvnw.cmd -q "-Dtest=DialogsControllerWebMvcTest,DialogAiOpsControllerWebMvcTest" test`

## Примечания

- Изменения не затрагивают backend-контракты `DialogAiOpsController` и переиспользуют уже существующие API страницы `AI Ops`.
- Runtime-файлы БД, PID и логи, изменённые вне этой задачи, намеренно не трогались.

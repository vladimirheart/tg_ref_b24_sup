# 2026-07-31 11:27:00 - auto-close-followup-transaction-isolation

## Затронутые области

- `ai-context/tasks/task-list.md`
- `ai-context/tasks/task-details/01-158.md`
- `java-bot/bot-core/src/main/java/com/example/supportbot/service/AutoCloseFollowUpTaskService.java`
- `java-bot/bot-core/src/test/java/com/example/supportbot/service/AutoCloseFollowUpTaskServiceTest.java`

## Пользовательский промт

> проверь, почему не срабатывает автозакрытие
>
> проверь, почему не срабатывает автозакрытие диалога по настройке

## Что сделано

- По логам `java-bot/bot-max/logs/bot-telegram.log` установлен фактический корень проблемы: scheduler доходил до auto-close, но после этого падал follow-up task с `SQLITE_CONSTRAINT_NOTNULL: task_links.user_id`.
- Зафиксирован рабочий вывод: side effect создания follow-up задачи не должен откатывать основное закрытие тикета.
- В `AutoCloseFollowUpTaskService` создание follow-up задачи вынесено в отдельную `REQUIRES_NEW` транзакцию через `TransactionTemplate`, чтобы сбой `task_links` не откатывал auto-close scheduler.
- В `AutoCloseFollowUpTaskServiceTest` добавлен regression test на сценарий, где `taskService.createTask(...)` падает, но вызов `createTaskForAutoClosedDialog(...)` не валит основной сценарий.
- Целевой тест прогнан через `java-bot\\mvnw.cmd -q -f java-bot/bot-core/pom.xml -Dtest=AutoCloseFollowUpTaskServiceTest test`.

## Остаточный риск

- Подлежащая отдельной доработке проблема `task_links.user_id` остаётся: follow-up task по auto-close всё ещё может не создаваться, но теперь она не должна ломать само автозакрытие диалога.

# 2026-06-10 12:58:42 - auto close follow up task

## Промпты пользователя

- `в проекте если диалог клиента не активен в течении определённого времени - он закрывается автоматически. при таких случаях нужно чтобы автоматически создавалась задача с тем-же отвтетсвенным, чей был диалог и соисполнителями, кто был в диалоге ещё. само собой в задаче должна быть ссылка на такой диалог`

## Что изменено

- в `java-bot` добавлен `AutoCloseFollowUpTaskService`, который после автозакрытия по неактивности создаёт follow-up задачу, если у диалога есть назначенный ответственный;
- новая задача получает того же `assignee`, собирает остальных участников диалога в `coExecutors`, прикладывает ссылку `/dialogs/<ticketId>` и связывается с диалогом через `task_links`;
- `TicketService.closeInactiveTickets(...)` теперь вызывает автосоздание follow-up задачи сразу после успешного `auto_close`;
- в bot-core schema-файлы добавлена таблица `ticket_participants`, чтобы структура участников была описана в штатной схеме, а не только создавалась рантаймом;
- добавлены unit-тесты на создание follow-up задачи и на сценарий без ответственного;
- рабочая задача зафиксирована в `ai-context/tasks/task-list.md` как `01-133`.

## Проверка

- `mvn -pl java-bot/bot-core test -Dtest=AutoCloseFollowUpTaskServiceTest,MaintenanceTasksTest`
- `git diff --check -- java-bot/bot-core/src/main/java/com/example/supportbot/service/AutoCloseFollowUpTaskService.java java-bot/bot-core/src/main/java/com/example/supportbot/service/TicketService.java java-bot/bot-core/src/main/resources/schema.sql java-bot/bot-core/src/main/resources/schema-sqlite.sql java-bot/bot-core/src/main/resources/schema-postgres.sql java-bot/bot-core/src/test/java/com/example/supportbot/service/AutoCloseFollowUpTaskServiceTest.java ai-context/tasks/task-list.md ai-context/changelog/2026-06-10_125842_auto-close-follow-up-task.md`

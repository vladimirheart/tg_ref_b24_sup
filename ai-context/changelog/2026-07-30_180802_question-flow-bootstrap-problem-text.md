# Question flow bootstrap problem text

## Промты пользователя

- `когда клиент пишет обращение, оно не записывается в историю, пока не опишет ответ на последний вопрос. это приведёт к негативу, например если написан большой текст, а в итоге он не попадает в обращение, потому что сначала нужно ответить на все вопросы. нужно на последнем вопросе добавлять тот текст, какой был написан до первого вопроса, но с учётом, что предыдущее обращение было закрыто`

## Что изменено

- В `java-bot/bot-core/src/main/java/com/example/supportbot/service/ConversationProblemTextSupport.java` добавлен общий helper для объединения стартового текста клиента с итоговым ответом на вопрос `problem` без грубого дублирования.
- В `java-bot/bot-core/src/test/java/com/example/supportbot/service/ConversationProblemTextSupportTest.java` добавлены тесты на базовые сценарии merge-логики.
- В `java-bot/bot-telegram/src/main/java/com/example/supportbot/telegram/SupportBot.java` стартовый текст клиента теперь сохраняется как bootstrap-event новой conversation session и подмешивается в итоговое значение `problem`.
- В `java-bot/bot-max/src/main/java/com/example/supportbot/max/MaxWebhookController.java` реализовано такое же поведение для MAX: первое содержательное сообщение клиента сохраняется для новой заявки и попадает в итоговый `problem` и `history`.
- В `java-bot/bot-vk/src/main/java/com/example/supportbot/vk/VkSupportBot.java` выровнено поведение VK-канала с Telegram/MAX, чтобы логика новых обращений не расходилась по платформам.
- В `ai-context/tasks/task-list.md` и `ai-context/tasks/task-details/01-156.md` зафиксирована и закрыта задача по task-flow проекта.

## Проверки

- `./mvnw.cmd -q -pl bot-core,bot-telegram,bot-max,bot-vk -am -DskipTests compile`
- `./mvnw.cmd -q -pl bot-core,bot-telegram,bot-max,bot-vk -am "-Dtest=ConversationProblemTextSupportTest,SupportBotTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`

## Примечания

- Изменение не вмешивается в открытые активные диалоги и срабатывает только в сценарии запуска нового обращения через question-flow.
- Внешние runtime-БД и логи, изменённые вне задачи, не трогались.

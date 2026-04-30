# 2026-04-30 09:23:41 - Починка приёма оценки в MAX-боте

## Что изменено

- В `java-bot/bot-max/src/main/java/com/example/supportbot/max/MaxWebhookController.java` добавлена обработка числовой оценки клиента по активному `pending_feedback_request` до запуска обычного flow диалога.
- MAX-бот теперь использует `FeedbackService` для поиска активного запроса оценки, сохранения `feedback` и отправки клиенту подтверждения после успешной записи.
- Для некорректного числа вне допустимой шкалы бот отвечает подсказкой `Отправьте число от 1 до N`, не переводя сообщение в новый сценарий обращения.
- В `java-bot/bot-max/src/test/java/com/example/supportbot/max/MaxWebhookControllerTest.java` добавлен тест на happy-path сохранения оценки и обновлён конструктор контроллера в существующем тесте.
- В `ai-context/tasks/task-list.md` и `ai-context/tasks/task-details/01-067.md` добавлена и описана задача на этот багфикс.

## Проверка

- `java-bot\\mvnw.cmd -q -pl=bot-max -am "-Dnet.bytebuddy.experimental=true" "-Dtest=MaxWebhookControllerTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`

## Примечания

- Во время первой попытки запуска тестов потребовался флаг `-Dnet.bytebuddy.experimental=true`, потому что локальный runtime использует Java 25, а inline-mockito без этого флага падает на ByteBuddy.

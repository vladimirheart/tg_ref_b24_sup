# 2026-04-30 10:16:12 - Приоритет ответов сценария над оценкой диалога

## Что изменено

- В `java-bot/bot-max/src/main/java/com/example/supportbot/max/MaxWebhookController.java` обработка числового `feedback` перенесена так, чтобы она срабатывала только при отсутствии активной сессии question-flow.
- В `java-bot/bot-telegram/src/main/java/com/example/supportbot/telegram/SupportBot.java` добавлено такое же ограничение: `tryHandleFeedback(...)` больше не перехватывает числа во время активного сценария обращения.
- В `java-bot/bot-max/src/test/java/com/example/supportbot/max/MaxWebhookControllerTest.java` добавлен регрессионный тест на ситуацию, когда число `2` внутри уже начатой сессии не должно считаться оценкой.
- В `ai-context/tasks/task-list.md` и `ai-context/tasks/task-details/01-068.md` добавлена отдельная задача на этот конфликт приоритетов.

## Проверка

- `java-bot\\mvnw.cmd -q -pl=bot-max -am "-Dnet.bytebuddy.experimental=true" "-Dtest=MaxWebhookControllerTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`
- `java-bot\\mvnw.cmd -q -pl=bot-telegram -am -DskipTests compile`

## Примечания

- Для `bot-max` по-прежнему требуется флаг `-Dnet.bytebuddy.experimental=true` в локальном окружении с Java 25, иначе inline-mockito падает на ByteBuddy.

# Изменения

- В `spring-panel/src/main/resources/static/js/bot-settings.js` для `Свободного вопроса` добавлен явный блок настройки ответа с переключателем обязательности, а флаг `required` теперь проходит через нормализацию, клонирование и сериализацию шаблонов.
- Кнопка `Сохранить шаблон` в редакторе шаблонов бота больше не ограничивается локальным состоянием страницы: после сохранения шаблона сразу вызывается реальная запись `bot_settings` в `/settings`, поэтому шаблон остаётся в `settings.json` и возвращается после перезагрузки страницы настроек.
- В `java-bot/bot-core/src/main/java/com/example/supportbot/settings/BotSettingsService.java` и `java-bot/bot-core/src/main/java/com/example/supportbot/settings/dto/QuestionFlowItemDto.java` добавлена поддержка поля `required`, чтобы санитизация и DTO не отбрасывали обязательность свободных вопросов.
- В `java-bot/bot-telegram/src/main/java/com/example/supportbot/telegram/SupportBot.java`, `java-bot/bot-vk/src/main/java/com/example/supportbot/vk/VkSupportBot.java` и `java-bot/bot-max/src/main/java/com/example/supportbot/max/MaxWebhookController.java` добавлен сценарий пропуска необязательного свободного вопроса через команду `Пропустить`, а в текстах prompt-сообщений появилось соответствующее пояснение.
- В `ai-context/tasks/task-list.md` добавлена задача `01-115` про обязательность свободных вопросов и исправление фактического сохранения шаблонов.

# Проверки

- `spring-panel\\mvnw.cmd -q -DskipTests compile`
- `java-bot\\mvnw.cmd -q -pl bot-core,bot-telegram,bot-vk,bot-max -am -DskipTests compile`

# Промпты пользователя

- `в настройках есть возмоность создавать шаблоны вопросов, но при выборе "Свободный вопрос" нужны поля ответов, которые могут быть обязательными или не обязательными.`
- `и ещё: при сохранении нового шаблона, он внутри системы вроде-бы сохраняется, т.к. в новом боте я увидел вопрос и создал обращение, но при повторном открытии страницы начтроек, этого шаблона нет`

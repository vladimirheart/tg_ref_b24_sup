# 2026-04-20 17:06:28

## Заголовок

UI списка диалогов и сайдбара уточнён, старт новой заявки в ботах защищён от двойного запуска

## Что изменено

- в списке диалогов кнопка `Открыть` вынесена отдельно, а `Действия`
  оставлены во втором раскрывающемся контроле для вторичных операций;
- workspace-лента сообщений слева стала компактнее: сообщения больше не
  растягиваются на всю ширину по умолчанию, уменьшены отступы, радиусы и размер
  мета-блока;
- в общем сайдбаре кнопка `Редактировать порядок` переставлена рядом с
  `Сменить пароль`, а не отдельной строкой выше карточки пользователя;
- в Telegram и VK стартовый auto-reply больше не отправляется поверх reuse-вопроса
  о прошлых значениях;
- в VK и MAX создание новой conversation session теперь завершает текущий цикл
  обработки сразу после первого prompt, поэтому стартовая входящая реплика не
  может в ту же секунду стать ответом и мгновенно создать заявку.

## Затронутые файлы

- `spring-panel/src/main/resources/templates/dialogs/index.html`
- `spring-panel/src/main/resources/static/js/dialogs.js`
- `spring-panel/src/main/resources/static/css/app.css`
- `spring-panel/src/main/resources/templates/fragments/navbar.html`
- `spring-panel/src/main/resources/static/css/sidebar.css`
- `java-bot/bot-telegram/src/main/java/com/example/supportbot/telegram/SupportBot.java`
- `java-bot/bot-vk/src/main/java/com/example/supportbot/vk/VkSupportBot.java`
- `java-bot/bot-max/src/main/java/com/example/supportbot/max/MaxWebhookController.java`
- `ai-context/tasks/task-list.md`

## Проверка

- `java-bot/.\\mvnw.cmd -q -DskipTests compile`
- `git diff --check -- spring-panel/src/main/resources/templates/dialogs/index.html spring-panel/src/main/resources/static/js/dialogs.js spring-panel/src/main/resources/static/css/app.css spring-panel/src/main/resources/templates/fragments/navbar.html spring-panel/src/main/resources/static/css/sidebar.css java-bot/bot-telegram/src/main/java/com/example/supportbot/telegram/SupportBot.java java-bot/bot-vk/src/main/java/com/example/supportbot/vk/VkSupportBot.java java-bot/bot-max/src/main/java/com/example/supportbot/max/MaxWebhookController.java ai-context/tasks/task-list.md`

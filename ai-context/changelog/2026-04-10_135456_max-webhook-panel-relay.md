# MAX webhook через spring-panel

- Время: `2026-04-10 13:54:57`
- Файлы:
  `spring-panel/src/main/java/com/example/panel/config/BotProcessProperties.java`,
  `spring-panel/src/main/java/com/example/panel/service/BotProcessService.java`,
  `spring-panel/src/main/java/com/example/panel/controller/MaxWebhookRelayController.java`,
  `spring-panel/src/main/java/com/example/panel/security/SecurityConfig.java`,
  `java-bot/bot-max/src/main/java/com/example/supportbot/max/MaxBotApplication.java`,
  `spring-panel/src/main/resources/templates/settings/index.html`,
  `docs/max_bot_setup.md`,
  `ai-context/rules/backend/01-bot-webhook-routing.md`,
  `ai-context/tasks/task-list.md`,
  `ai-context/tasks/task-details/01-010.md`
- Что сделано:
  MAX-бот переведен на корректную production-схему webhook через public origin
  панели: panel принимает внешний webhook по пути
  `/webhooks/max/{channelId}` и проксирует его во внутренний локальный
  процесс `bot-max`, который теперь запускается как servlet-сервис на
  `127.0.0.1` и отдельном порту для канала; дополнительно обновлены security,
  UI-подсказки, документация и зафиксировано архитектурное правило routing-а
  webhook-платформ через `spring-panel`.

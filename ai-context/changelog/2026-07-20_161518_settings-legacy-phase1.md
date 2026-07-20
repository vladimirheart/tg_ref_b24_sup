# 2026-07-20 16:15:18 - settings legacy phase1

- Затронутые области:
  - `ai-context/tasks/task-list.md`
  - `ai-context/tasks/task-details/01-150.md`
  - `spring-panel/src/main/java/com/example/panel/service/SettingsTopLevelUpdateService.java`
  - `spring-panel/src/test/java/com/example/panel/service/SettingsTopLevelUpdateServiceTest.java`
  - `java-bot/bot-core/src/main/java/com/example/supportbot/settings/BotSettingsService.java`
  - `java-bot/bot-core/src/test/java/com/example/supportbot/settings/BotSettingsServiceTest.java`
- Пользовательский промпт:
  - `приступи к выполнению задачи 01-150`
- Что сделано:
  - задача `01-150` переведена в статус `🟡`, а detail-файл дополнен текущим execution slice для первого безопасного этапа cleanup;
  - на save-boundary `SettingsTopLevelUpdateService` `auto_close_hours` переведён в derived mirror от активного `auto_close_config` template с предупреждением при legacy mismatch;
  - добавлена deprecation-диагностика для legacy fallback-путей `bot_settings.question_flow`, `bot_settings.rating_system` и `channel.questions_cfg` в `BotSettingsService` и `SettingsTopLevelUpdateService`;
  - добавлены regression tests для derived `auto_close_hours` и для импорта legacy `question_flow` / `rating_system` в канонические template-структуры.
- Проверки:
  - `java-bot\\mvnw.cmd -pl bot-core test "-Dtest=BotSettingsServiceTest,MaintenanceTasksTest"` — success
  - `spring-panel\\mvnw.cmd -DskipTests compile` — success

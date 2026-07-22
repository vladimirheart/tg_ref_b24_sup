# 2026-07-22 09:14:44 — template auto close hour key removal

## Контекст
- Пользователь: `забирай техдолг`
- Значимый контекст из `01-150`: после финализации audit/removal-plan следующим безопасным implementation-срезом оставался template-level cleanup для deprecated `timeout_hours` / `auto_close_hours` внутри `auto_close_config.templates[*]`.

## Что сделано
- В `spring-panel/src/main/java/com/example/panel/service/AutoCloseConfigNormalizer.java` снят compatibility import deprecated template hour keys:
  - canonical ключом остаётся только `hours`;
  - `timeout_hours` / `auto_close_hours` больше не импортируются в canonical template;
  - если template содержит только deprecated keys, panel пишет warning и отбрасывает такой template при normalizing.
- В `java-bot/bot-core/src/main/java/com/example/supportbot/service/MaintenanceTasks.java` bot runtime больше не использует template-level deprecated hour keys:
  - `extractTemplateHours(...)` читает только canonical `hours`;
  - при встрече `timeout_hours` / `auto_close_hours` runtime пишет warning и считает template невалидным;
  - auto-close policy уходит в обычный default/fallback path вместо compatibility import.
- В тестах зафиксирована новая политика:
  - `spring-panel/src/test/java/com/example/panel/service/SettingsTopLevelUpdateServiceTest.java`
  - `spring-panel/src/test/java/com/example/panel/service/SettingsUpdateSharedConfigIntegrationTest.java`
  - `java-bot/bot-core/src/test/java/com/example/supportbot/service/MaintenanceTasksTest.java`
  - deprecated template hour keys теперь проверяются как ignored deprecated residue, а не как рабочий compatibility contract.
- В `ai-context/tasks/task-details/01-150.md` обновлены execution log, removal-plan, текущая точка остановки и следующий шаг.

## Проверки
- `spring-panel\\mvnw.cmd "-Dtest=SettingsTopLevelUpdateServiceTest,SettingsUpdateSharedConfigIntegrationTest" test`
- `java-bot\\mvnw.cmd -pl bot-core "-Dtest=MaintenanceTasksTest" test`

## Следующий шаг
- Решить, закрывается ли `01-150` как completed audit/removal-preparation slice, или брать отдельный rollout по оставшимся root-level/channel migration-only import-paths.

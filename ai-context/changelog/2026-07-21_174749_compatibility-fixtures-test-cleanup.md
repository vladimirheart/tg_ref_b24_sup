# 2026-07-21 17:47:49 — compatibility fixtures and tests cleanup

## Контекст
- Пользователь: `теперь давай по "
- отдельно пройтись по остаточным consumers/tests/fixtures, где top-level \`auto_close_hours\`, legacy \`question_flow\` / \`rating_system\` и template-level deprecated hour keys ещё фигурируют как входная совместимость, а не как canonical contract;"`
- Значимый контекст из `01-150`: после перевода `auto_close_hours` в migration-only write/read policy оставалось пройтись по тестам и fixture-сценариям, чтобы legacy input-path больше не выглядел normal-path контрактом.

## Что сделано
- В `spring-panel/src/test/java/com/example/panel/controller/ManagementControllerWebMvcTest.java` обычный bootstrap test переведён на canonical auto-close fixture:
  - вместо legacy `timeout_hours` теперь используется canonical `hours`;
  - legacy bot settings import test переименован так, чтобы было ясно, что это именно import compatibility scenario.
- В `java-bot/bot-core/src/test/java/com/example/supportbot/settings/BotSettingsServiceTest.java` generic sanitize coverage очищен от legacy mirrors:
  - общий test теперь проверяет нормализацию canonical `question_templates` / `rating_templates` без `question_flow` / `rating_system` на корне;
  - добавлен отдельный explicit test на precedence canonical templates over legacy root mirrors;
  - import test переименован так, чтобы отражать условие “только когда canonical templates отсутствуют”.
- В `java-bot/bot-core/src/test/java/com/example/supportbot/service/MaintenanceTasksTest.java` тесты с top-level `auto_close_hours` переименованы в migration-only terminology, чтобы не закреплять старое поле как обычный runtime contract.
- Historical snapshot `temp-recovery/routing-migration-backup-2026-07-08_085737/settings.json` сознательно не переписывался:
  - это backup старого состояния для recovery/manual audit;
  - он не используется как canonical runtime/test fixture.
- В `ai-context/tasks/task-details/01-150.md` обновлены execution log, точка остановки и следующий шаг: обычные tests/bootstrap fixtures теперь canonical-first, а следующий cleanup смещён на фактическое удаление runtime fallback `migration:auto_close_hours`.

## Проверки
- `java-bot\\mvnw.cmd -pl bot-core "-Dtest=BotSettingsServiceTest,MaintenanceTasksTest" test`
- `spring-panel\\mvnw.cmd "-Dtest=ManagementControllerWebMvcTest,SettingsTopLevelUpdateServiceTest,SettingsUpdateSharedConfigIntegrationTest" test`

## Следующий шаг
- Удалять runtime read-fallback `migration:auto_close_hours` из `MaintenanceTasks`, а затем переходить к аудиту и cleanup root `unblock_request_cooldown_minutes`.

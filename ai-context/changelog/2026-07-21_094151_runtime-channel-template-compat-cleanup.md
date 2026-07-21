# 2026-07-21 09:41:51 — runtime channel template compatibility cleanup

## Контекст
- Пользователь: `давай следующий шаг "добить cleanup channel compatibility-слоя в runtime"`
- Задача: `ai-context/tasks/task-details/01-150.md`

## Что сделано
- В `java-bot/bot-core/src/main/java/com/example/supportbot/settings/BotSettingsService.java` runtime cleanup для channel template selection доведён до конца:
  - удалён read-fallback на `questions_cfg.active_template_id/question_template_id`;
  - удалён read-fallback на `questions_cfg.active_rating_template_id/rating_template_id`;
  - активные question/rating template override теперь берутся только из typed channel fields `question_template_id` и `rating_template_id`;
  - legacy keys в `questions_cfg` оставлены только как migration diagnostics с явным логом, что runtime их игнорирует.
- В `java-bot/bot-core/src/test/java/com/example/supportbot/settings/BotSettingsServiceTest.java` добавлены regression-тесты:
  - typed channel overrides применяются поверх shared bot settings;
  - legacy template selection в `questions_cfg` больше не влияет на runtime, если typed overrides пустые.
- В `ai-context/tasks/task-details/01-150.md` обновлены execution log, текущая точка остановки и следующий шаг.

## Проверки
- `java-bot\mvnw.cmd -pl bot-core test "-Dtest=BotSettingsServiceTest,MaintenanceTasksTest"`
- `java-bot\mvnw.cmd -pl bot-core -DskipTests compile`

## Следующий шаг
- Сузить оставшиеся общие page/runtime fallback helper-ы вокруг settings bootstrap contract, а затем отдельно пройтись по derived/deprecated mirror-полям `question_flow`, `rating_system`, `auto_close_hours`.

# 2026-07-21 17:23:19 — runtime/public mirror compatibility cleanup

## Контекст
- Пользователь: `давай по 
- отдельно пройтись по runtime/public compatibility для оставшихся derived/deprecated mirror-полей (\`question_flow\`, \`rating_system\`, \`auto_close_hours\`);`
- Значимый контекст из `01-150`: после canonical-first runtime cutover и JSON cleanup оставалось отдельно пройтись по остаточным public/config/runtime проявлениям derived mirror-полей и сделать явной границу между canonical contract и legacy input compatibility.

## Что сделано
- В `config/shared/settings.json` удалены root-level derived mirrors внутри `bot_settings`:
  - `question_flow`;
  - `rating_system`.
- В `java-bot/bot-core/src/main/java/com/example/supportbot/service/MaintenanceTasks.java` сохранена runtime-совместимость с deprecated template hour keys внутри `auto_close_config.templates[*]`, но она стала явной:
  - при чтении `timeout_hours` пишется warning о deprecated runtime path;
  - при чтении `auto_close_hours` внутри template тоже пишется warning о deprecated runtime path;
  - canonical key для runtime остаётся `hours`.
- В `java-bot/bot-core/src/main/java/com/example/supportbot/service/TicketService.java` auto-close источники больше не протекают в user-facing текст причины закрытия:
  - legacy/config/runtime source identifiers для автозакрытия теперь сводятся к единому сообщению про автоматическое закрытие из-за отсутствия активности;
  - legacy duration overload помечен более честным internal source `legacy:auto_close_duration_api`.
- В `java-bot/bot-core/src/test/java/com/example/supportbot/service/MaintenanceTasksTest.java` добавлен regression test, который фиксирует, что template-level deprecated `auto_close_hours` всё ещё работает только как compatibility input для старого runtime config.
- В `ai-context/tasks/task-details/01-150.md` обновлены execution log, точка остановки и следующий шаг: root mirrors убраны из canonical fixture-config, а следующий cleanup смещён в removal top-level `auto_close_hours` fallback и оставшиеся legacy input expectations.

## Проверки
- `java-bot\\mvnw.cmd -pl bot-core "-Dtest=MaintenanceTasksTest" test`

## Следующий шаг
- Отдельно решить removal-план для top-level `auto_close_hours` в `MaintenanceTasks`, затем пройтись по остаточным legacy input-path expectations/fixtures и после этого брать cleanup root `unblock_request_cooldown_minutes`.

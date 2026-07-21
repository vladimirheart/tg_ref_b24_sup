# 2026-07-21 15:36:44 — auto close legacy fallback scope

## Контекст
- Пользователь: `бери в работу следующий крупный шаг по задаче`
- Значимый контекст из `01-150`: после canonical-first cleanup в `BotSettingsService` следующий крупный шаг был смещён на `MaintenanceTasks.auto_close_hours` fallback и связанные fixtures.

## Что сделано
- В `java-bot/bot-core/src/main/java/com/example/supportbot/service/MaintenanceTasks.java` legacy `auto_close_hours` переведён в явный read-only fallback:
  - fallback срабатывает только когда `auto_close_config` отсутствует целиком;
  - если `auto_close_config` уже есть, но не дал валидный template selection, runtime больше не подхватывает root `auto_close_hours` как скрытую запасную ветку;
  - для таких случаев добавлен явный warning, что deprecated `auto_close_hours` проигнорирован, потому что canonical `auto_close_config` уже присутствует.
- В `java-bot/bot-core/src/test/java/com/example/supportbot/service/MaintenanceTasksTest.java` добавлены regression tests:
  - `resolveAutoCloseDuration` не должен падать обратно в legacy `auto_close_hours`, если `auto_close_config` уже существует;
  - scheduler policy не должен использовать `legacy:auto_close_hours`, если canonical config есть, но пустой.
- В `config/shared/settings.json` удалён top-level `auto_close_hours`, потому что в репозиторном shared config уже есть canonical `auto_close_config` с активным template.
- В `ai-context/tasks/task-details/01-150.md` обновлены execution log, текущая точка остановки и следующий шаг: дальше cleanup смещается на публичный bot settings contract и на deprecated template keys внутри auto-close templates.

## Проверки
- `java-bot\mvnw.cmd -pl bot-core "-Dtest=MaintenanceTasksTest" test`

## Следующий шаг
- Отдельно решить судьбу публичных compatibility mirrors `question_flow` / `rating_system` в `BotSettingsDto` / bootstrap API, а затем пройтись по deprecated auto-close template keys `timeout_hours` / `auto_close_hours` внутри template payload.

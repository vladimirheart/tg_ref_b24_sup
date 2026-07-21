# 2026-07-21 12:31:56 — settings derived mirror save cleanup

## Контекст
- Пользователь: `давай по следующему шагу "отдельно пройтись по оставшимся derived/deprecated mirror-полям (\`question_flow\`, \`rating_system\`, \`auto_close_hours\`)"`
- Значимое уточнение: нужно продолжать задачу `ai-context/tasks/task-details/01-150.md` и зафиксировать, на каком этапе она останавливается и какой следующий шаг.

## Что сделано
- В `spring-panel/src/main/java/com/example/panel/service/BotSettingsPayloadNormalizer.java` сохранение `bot_settings` переведено на canonical-only persistence:
  - legacy `bot_settings.question_flow` по-прежнему импортируется в `question_templates`, если canonical templates отсутствуют;
  - legacy `bot_settings.rating_system` по-прежнему импортируется в `rating_templates`, если canonical templates отсутствуют;
  - после нормализации deprecated mirrors `question_flow` и `rating_system` больше не записываются обратно в shared config как persisted root-level поля.
- В `spring-panel/src/main/java/com/example/panel/service/SettingsTopLevelUpdateService.java` `auto_close_config` сделан единственным write-side источником истины:
  - при наличии `auto_close_config` legacy `auto_close_hours` больше не вычисляется из active template и не сохраняется как зеркало;
  - если в одном payload пришли и `auto_close_config`, и `auto_close_hours`, legacy поле игнорируется с явным логом.
- В `spring-panel/src/test/java/com/example/panel/service/SettingsTopLevelUpdateServiceTest.java` и `spring-panel/src/test/java/com/example/panel/service/SettingsUpdateSharedConfigIntegrationTest.java` обновлены проверки:
  - сохранение `bot_settings` должно оставлять canonical `question_templates` / `rating_templates` без persisted mirrors `question_flow` / `rating_system`;
  - сохранение `auto_close_config` не должно восстанавливать `auto_close_hours`;
  - legacy import-сценарии продолжают мигрировать старые payload в canonical templates.
- В `ai-context/tasks/task-details/01-150.md` обновлены execution log, текущая точка остановки и следующий шаг: дальше cleanup смещается с panel save-boundary на runtime/public contract (`BotSettingsService`, `MaintenanceTasks`, fixtures).

## Проверки
- `spring-panel\mvnw.cmd "-Dtest=SettingsTopLevelUpdateServiceTest,SettingsUpdateSharedConfigIntegrationTest" test`

## Следующий шаг
- Отдельно пройтись по runtime/public compatibility для `question_flow`, `rating_system` и `auto_close_hours`: решить, какие read-fallback и derived outputs можно убирать из `BotSettingsService`, `MaintenanceTasks` и связанных DTO/API consumers без риска для старых конфигов.

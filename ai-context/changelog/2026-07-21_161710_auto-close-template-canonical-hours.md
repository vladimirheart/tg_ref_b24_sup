# 2026-07-21 16:17:10 — auto close template canonical hours

## Контекст
- Пользователь: `продолжи более крупным пакетом`
- Значимый контекст из `01-150`: следующий крупный срез был сфокусирован на оставшихся derived/deprecated mirror-полях и на cleanup deprecated template hour keys внутри `auto_close_config`.

## Что сделано
- В `spring-panel/src/main/java/com/example/panel/service/AutoCloseConfigNormalizer.java` добавлен server-side normalizer для `auto_close_config`:
  - нормализует `templates[*].hours`;
  - импортирует deprecated `timeout_hours` / `auto_close_hours` только на migration boundary;
  - вычищает deprecated hour keys из результата;
  - вычисляет fallback hours для bootstrap settings page.
- В `spring-panel/src/main/java/com/example/panel/service/SettingsTopLevelUpdateService.java` сохранение `auto_close_config` переведено на normalizer, чтобы shared config всегда получал canonical template payload.
- В `spring-panel/src/main/java/com/example/panel/controller/ManagementController.java` и `spring-panel/src/main/resources/templates/settings/index.html` settings bootstrap переведён на нормализованный `auto_close_config` и вычисленный `autoCloseFallbackHours`.
- Во фронтенде убраны normal-path fallback-ветки на deprecated payload:
  - `spring-panel/src/main/resources/static/js/settings-dialog-templates-runtime.js` теперь читает только canonical `hours`;
  - `spring-panel/src/main/resources/static/js/settings-channel-templates-runtime.js` больше не резюмирует legacy `template.questions`;
  - `spring-panel/src/main/resources/static/js/bot-settings.js` больше не опирается на root `question_flow` / `rating_system` и deprecated `raw.questions` для settings page runtime; legacy diagnostic переведён в подтверждение канонической схемы.
- Добавлены regression tests:
  - `spring-panel/src/test/java/com/example/panel/service/SettingsTopLevelUpdateServiceTest.java`
  - `spring-panel/src/test/java/com/example/panel/service/SettingsUpdateSharedConfigIntegrationTest.java`
  - `spring-panel/src/test/java/com/example/panel/controller/ManagementControllerWebMvcTest.java`
  Эти тесты проверяют нормализацию legacy template hour keys в canonical `hours` и canonical bootstrap `/settings`.
- В `ai-context/tasks/task-details/01-150.md` обновлены execution log, текущая точка остановки и следующий шаг.

## Проверки
- `spring-panel\\mvnw.cmd -DskipTests compile`
- Адресный прогон 26 тестов через локальный JUnit launcher:
  - `SettingsTopLevelUpdateServiceTest`
  - `SettingsUpdateSharedConfigIntegrationTest`
  - `ManagementControllerWebMvcTest`
- Полный `spring-panel` `testCompile` по-прежнему падает на уже существующих посторонних тестах dialog/workspace (`DialogConversationReadServiceTest`, `DialogWorkspaceParityServiceTest`, `DialogWorkspacePayloadAssemblerServiceTest`, `DialogApiControllerWebMvcTest`, `DialogWorkspaceHistorySliceServiceTest`, `DialogDetailsReadServiceTest`), не связанных с этим пакетом изменений.

## Следующий шаг
- Следующим крупным шагом отдельно пройтись по публичному compatibility contract `BotSettingsDto` и решить судьбу root-level mirrors `question_flow` / `rating_system`, а затем добить финальный removal-план для legacy `auto_close_hours` read-fallback в runtime.

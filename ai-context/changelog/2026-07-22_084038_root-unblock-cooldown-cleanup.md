# 2026-07-22 08:40:38 — root unblock cooldown cleanup

## Контекст
- Пользователь: `бери в работу следующий шаг по задаче`
- Значимый контекст из `01-150`: после снятия runtime fallback для top-level `auto_close_hours` следующим cleanup-срезом оставался аудит и выведение из canonical contract root `unblock_request_cooldown_minutes`.

## Что сделано
- В `spring-panel/src/main/java/com/example/panel/service/BotSettingsPayloadNormalizer.java` добавлена canonical-first нормализация cooldown:
  - canonical источником считается `bot_settings.unblock_request_cooldown_minutes`;
  - deprecated root `unblock_request_cooldown_minutes` импортируется только если nested canonical поле отсутствует;
  - при конфликте nested значение сохраняется, а root duplicate игнорируется с диагностическим логом.
- В `spring-panel/src/main/java/com/example/panel/service/SettingsTopLevelUpdateService.java` и `spring-panel/src/main/java/com/example/panel/controller/ManagementController.java` panel boundary переведён на migration-only работу с root cooldown:
  - при сохранении canonical `bot_settings` root duplicate удаляется из payload;
  - legacy top-level payload без `bot_settings` мигрируется в canonical nested поле;
  - bootstrap `/settings` публикует уже очищенный `bot_settings` без root duplicate.
- В `java-bot/bot-core/src/main/java/com/example/supportbot/settings/BotSettingsService.java` bot runtime переведён на canonical shared settings:
  - default/channel loading теперь строится от canonical `bot_settings`, а не от полного shared settings map;
  - root `unblock_request_cooldown_minutes` поддерживается только как compatibility input для старых snapshots;
  - при наличии nested canonical cooldown root duplicate больше не участвует в normal-path выборе значения.
- В `config/shared/settings.json` удалён root `unblock_request_cooldown_minutes`, чтобы репозиторный shared config не публиковал deprecated duplicate как актуальный contract.
- Добавлены и обновлены tests:
  - `spring-panel/src/test/java/com/example/panel/service/SettingsTopLevelUpdateServiceTest.java`
  - `spring-panel/src/test/java/com/example/panel/service/SettingsUpdateSharedConfigIntegrationTest.java`
  - `spring-panel/src/test/java/com/example/panel/controller/ManagementControllerWebMvcTest.java`
  - `java-bot/bot-core/src/test/java/com/example/supportbot/settings/BotSettingsServiceTest.java`
  - отдельно поправлен brittle WebMvc test, чтобы он проверял canonical bootstrap payload, а не случайные `:1` в полном HTML.
- В `ai-context/tasks/task-details/01-150.md` обновлены execution log, текущая точка остановки и следующий крупный шаг.

## Проверки
- `spring-panel\\mvnw.cmd "-Dtest=ManagementControllerWebMvcTest,SettingsTopLevelUpdateServiceTest,SettingsUpdateSharedConfigIntegrationTest" test`
- `java-bot\\mvnw.cmd -pl bot-core "-Dtest=BotSettingsServiceTest" test`

## Следующий шаг
- Перейти к финальному аудиту shared settings schema и remaining migration-only input-paths, включая решение по `dialog_config.question_templates` и historical recovery snapshots.

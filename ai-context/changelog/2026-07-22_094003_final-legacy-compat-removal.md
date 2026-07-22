# 2026-07-22 09:40:03 — final legacy compat removal

## Контекст
- Пользователь: `доделай техдолг по задаче - мусор оставлять не правильно и опасно`
- Значимый контекст из `01-150`: после cleanup template-level auto-close key-ов оставались migration-only helper-ы на panel/runtime/channel boundary для `question_flow`, `rating_system`, root/top-level mirror-полей и legacy template selection в `questions_cfg`.

## Что сделано
- В `spring-panel/src/main/java/com/example/panel/service/BotSettingsPayloadNormalizer.java` удалён operational import legacy mirror-полей:
  - `bot_settings.question_flow` и `bot_settings.rating_system` больше не импортируются в canonical templates;
  - root `unblock_request_cooldown_minutes` больше не импортируется в nested `bot_settings`;
  - deprecated payload теперь только warning-ится и вычищается из canonical output.
- В `spring-panel/src/main/java/com/example/panel/service/SettingsTopLevelUpdateService.java`,
  `spring-panel/src/main/java/com/example/panel/controller/ManagementController.java` и
  `spring-panel/src/main/java/com/example/panel/service/AutoCloseConfigNormalizer.java` снят последний panel/bootstrap compatibility для root/top-level mirror-полей:
  - top-level `auto_close_hours` больше не мигрируется в `auto_close_config`;
  - bootstrap fallback `autoCloseFallbackHours` теперь считается только из canonical `auto_close_config`;
  - root `unblock_request_cooldown_minutes` больше не пересобирается в canonical `bot_settings`.
- В `java-bot/bot-core/src/main/java/com/example/supportbot/settings/BotSettingsService.java` runtime окончательно переведён на canonical-only bot settings:
  - больше нет fallback на `channel.questions_cfg` как источник bot settings;
  - больше нет import root `unblock_request_cooldown_minutes`;
  - legacy `question_flow` / `rating_system` больше не восстанавливают canonical templates;
  - helper-методы question/rating больше не используют root mirror как запасной operational source.
- В `spring-panel/src/main/java/com/example/panel/service/ChannelTransportService.java` снят channel compatibility backfill:
  - `questions_cfg.active_template_id/question_template_id` и `active_rating_template_id/rating_template_id` больше не продвигаются в typed channel fields;
  - API response больше не backfill-ит `question_template_id` / `rating_template_id` из `questions_cfg`;
  - `questions_cfg` остаётся только normalizable channel-local JSON без template selection contract.
- Тестовый контракт переписан под ignored-deprecated policy:
  - `java-bot/bot-core/src/test/java/com/example/supportbot/settings/BotSettingsServiceTest.java`
  - `spring-panel/src/test/java/com/example/panel/controller/ManagementControllerWebMvcTest.java`
  - `spring-panel/src/test/java/com/example/panel/controller/ChannelApiControllerWebMvcTest.java`
  - `spring-panel/src/test/java/com/example/panel/service/SettingsTopLevelUpdateServiceTest.java`
  - `spring-panel/src/test/java/com/example/panel/service/SettingsUpdateSharedConfigIntegrationTest.java`
  - legacy payload теперь проверяется как ignored residue, а не как migration/import contract.
- В `ai-context/tasks/task-details/01-150.md` обновлены execution log, removal plan, текущая точка остановки и следующий шаг.
- В `ai-context/tasks/task-list.md` задача `01-150` переведена в `🟣` как завершённая AI и ожидающая ручной проверки.

## Проверки
- `spring-panel\\mvnw.cmd "-Dtest=ManagementControllerWebMvcTest,ChannelApiControllerWebMvcTest,SettingsTopLevelUpdateServiceTest,SettingsUpdateSharedConfigIntegrationTest" test`
- `java-bot\\mvnw.cmd -pl bot-core "-Dtest=BotSettingsServiceTest,MaintenanceTasksTest" test`

## Следующий шаг
- Ручная проверка результата по `01-150`.
- Если понадобится дополнительное продолжение, выносить его в отдельную задачу про archival/input residue (`BotSettingsDto` legacy setter-ы, warning-only diagnostics, historical recovery snapshots), а не возвращать compatibility-path в основной runtime/panel contract.

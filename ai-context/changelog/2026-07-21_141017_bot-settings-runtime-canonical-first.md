# 2026-07-21 14:10:17 — bot settings runtime canonical-first

## Контекст
- Пользователь: `бери в работу следующий шаг по задаче`
- Значимый контекст из `01-150`: после cleanup save-boundary следующий шаг был смещён на runtime/public compatibility для `question_flow`, `rating_system` и `auto_close_hours`.

## Что сделано
- В `java-bot/bot-core/src/main/java/com/example/supportbot/settings/BotSettingsService.java` runtime-логика переведена на canonical-first чтение активных bot templates:
  - `ratingScale`, `ratingPrompt` и `ratingResponses` теперь вычисляются от active `rating_templates`, а не от root `rating_system` как источника истины;
  - `startAutoReply` использует active question template напрямую;
  - channel-level template overrides больше не мерджат root `question_flow` / `rating_system` вручную как самостоятельные поля, а только переключают active ids;
  - compatibility mirrors `question_flow` и `rating_system` теперь пересобираются централизованно из active templates одним helper после sanitize/default/channel-override этапов.
- В том же сервисе fallback scale для default rating normalization переведён с root `defaults.rating_system.scale_size` на active default rating template, чтобы внутренняя логика меньше зависела от deprecated mirror-структуры.
- В `java-bot/bot-core/src/test/java/com/example/supportbot/settings/BotSettingsServiceTest.java` добавлен regression test, который подтверждает: helper-методы рейтингов предпочитают active rating template, даже если в DTO лежит устаревший root `rating_system`.
- В `ai-context/tasks/task-details/01-150.md` обновлены execution log, текущая точка остановки и следующий шаг: дальше cleanup смещается на `MaintenanceTasks.auto_close_hours` fallback и только потом на возможное сужение публичного DTO/API контракта.

## Проверки
- `java-bot\mvnw.cmd -pl bot-core "-Dtest=BotSettingsServiceTest" test`

## Следующий шаг
- Отдельно пройтись по `MaintenanceTasks` и связанным fixtures, чтобы сделать `auto_close_hours` явно временным read-only fallback для старых конфигов и подготовить условия для его удаления.

# 2026-07-01 09:19:00 — dialogs watcher regression fix

## Prompt

`сделай тогда: Следующий рациональный шаг уже не очередной вынос, а финальный regression pass по ключевым сценариям страницы диалогов`

Значимое продолжение:

`давай дальше`

## Что изменено

- В `spring-panel` `OperatorNotificationWatcher` перестал реплеить старые
  `chat_history` сообщения как live incoming events: replay теперь ограничен
  окном свежести по `timestamp`, чтобы исторические seeded/follow-up записи не
  создавали повторные bell/operator notifications и не запускали лишний
  AI-processing.
- В `OperatorNotificationWatcherTest` добавлен отдельный regression-сценарий,
  который фиксирует, что историческое клиентское сообщение вне replay-window
  не вызывает `notifyIncomingClientMessage`, `notifyAllOperators` и
  `processIncomingClientMessage`.
- В `ai-context/tasks/task-details/01-130.md` и
  `docs/ARCH_UI_REFACTORING_ROADMAP_2026-04-15.md` зафиксировано, что
  остаточный dialogs regression corridor закрыт именно watcher continuity-fix и
  sequential integration re-run, а не новым helper-split.

## Проверка

- `./mvnw.cmd -q -DskipTests compile`
- `./mvnw.cmd -q "-Dtest=OperatorNotificationWatcherTest" test`
- `./mvnw.cmd -q "-Dtest=DialogDetailsIntegrationTest" test`
- `./mvnw.cmd -q "-Dtest=DialogWorkspaceIntegrationTest" test`
- `./mvnw.cmd -q "-Dtest=DialogQuickActionsIntegrationTest" test`

## Затронутые файлы

- `spring-panel/src/main/java/com/example/panel/service/OperatorNotificationWatcher.java`
- `spring-panel/src/test/java/com/example/panel/service/OperatorNotificationWatcherTest.java`
- `ai-context/tasks/task-details/01-130.md`
- `docs/ARCH_UI_REFACTORING_ROADMAP_2026-04-15.md`

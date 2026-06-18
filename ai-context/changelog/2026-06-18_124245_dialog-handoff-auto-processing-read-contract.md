# 2026-06-18 12:42:45 - dialog handoff auto processing read contract

## Промпты пользователя

- `давай следующий шаг`

## Что изменено

- в `spring-panel/src/test/java/com/example/panel/controller/DialogListIntegrationTest.java`
  расширен сценарий `listApiProjectsOwnerHandoffAndAutoProcessingBucketsForNewOwner()`,
  чтобы явно проверить reread после live `POST /reassign`;
- в `spring-panel/src/test/java/com/example/panel/controller/DialogDetailsIntegrationTest.java`
  расширен сценарий `dialogsListTransfersMyDialogsOwnershipAcrossReassignAndNextFollowUp()`
  теми же post-reassign проверками для owner handoff;
- зафиксирован текущий runtime-контракт read-side consumer'ов:
  seeded dialog может стартовать как `auto_processing`, но после `reassign`
  reread уже показывает `aiProcessing=false` и `statusKey=waiting_client`;
- подтверждено, что handoff не ломает bucket continuity:
  старый owner сразу теряет dialog из `my_dialogs`, новый owner получает его
  в `my_dialogs.in_work`, а следующий клиентский follow-up возвращает dialog
  в `waiting_operator` / `my_dialogs.unanswered`;
- обновлены `docs/ARCHITECTURE_AUDIT_2026-04-08.md` и
  `docs/ARCH_UI_REFACTORING_ROADMAP_2026-04-15.md`, чтобы этот observed
  контракт не потерялся перед следующим dialog runtime пакетом.

## Проверка

- `.\mvnw.cmd "-Dtest=DialogListIntegrationTest#listApiProjectsOwnerHandoffAndAutoProcessingBucketsForNewOwner" test`
- `.\mvnw.cmd "-Dtest=DialogDetailsIntegrationTest#dialogsListTransfersMyDialogsOwnershipAcrossReassignAndNextFollowUp" test`
- `git diff --check`

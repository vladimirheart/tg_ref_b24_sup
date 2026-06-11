# 2026-06-11 18:01:05 - dialog quick actions refresh loop

## Промпты пользователя

- `продолжи`

## Что изменено

- в `spring-panel/src/test/java/com/example/panel/controller/DialogQuickActionsIntegrationTest.java` добавлен live-сценарий `quickActionsApiTakeAndCategoriesPersistAcrossReplyAckAndRepeatedFollowUpRefreshLoop`, который закрепляет цепочку `take -> categories -> reply -> first follow-up -> dialogs/details/workspace reread -> bell read-ack -> second follow-up`;
- тот же пакет добрал helper `plusMinutes(...)`, чтобы integration-сценарий работал с реальными runtime timestamp format'ами из SQLite (`Z`, offset и local datetime), а не только с одним локальным шаблоном;
- по ходу стабилизации обновлены несколько quick-action assertions под текущий runtime contract: unknown-target notification baseline, reopen bucket semantics, bell/read checks и post-ack list placement для `waiting_operator`;
- в `docs/ARCHITECTURE_AUDIT_2026-04-08.md` зафиксировано, что post-action refresh loop теперь покрыт live-тестом и что следующий узкий фокус смещается с quick-action transport parity на явную нормализацию `unreadCount` / `my_dialogs` / bell semantics;
- в `docs/ARCH_UI_REFACTORING_ROADMAP_2026-04-15.md` Track B дополнен отдельным runtime-focus блоком про repeated follow-up refresh corridor как обязательный regression boundary перед большим split `dialogs.js`.

## Проверка

- `.\mvnw.cmd '-Dtest=DialogQuickActionsIntegrationTest' test`
- `.\mvnw.cmd '-Dtest=DialogQuickActionsIntegrationTest,DialogQuickActionsControllerWebMvcTest,DialogListIntegrationTest,DialogReadIntegrationTest,DialogWorkspaceIntegrationTest,NotificationApiIntegrationTest' test`

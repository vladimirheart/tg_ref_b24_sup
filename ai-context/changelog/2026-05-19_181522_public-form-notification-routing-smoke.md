# 2026-05-19 18:15:22 - Public form notification routing smoke

## Промт пользователя

- `давай дальше`
- `продолжи`

## Что сделано

- Расширен `PublicFormFlowSmokeIntegrationTest` на runtime-слой
  `public-form -> notification routing`.
- Добавлен follow-up сценарий, который создаёт `web_form` тикет,
  проводит operator take/reply, добавляет client follow-up и фиксирует
  bell notification creation плюс read-reset через live
  `NotificationService summary` и `notifications` table.
- Добавлен соседний participant-lifecycle сценарий, который закрепляет
  peer-notification routing для `resolve/reopen` веток и одновременно
  проверяет `resolved/categories` continuity самого dialog.
- Тестовая очистка в `PublicFormFlowSmokeIntegrationTest` расширена на
  `ticket_active`, `ticket_responsibles`, `notifications`,
  `dialog_action_audit` и временных `watcher_*` users, чтобы новый smoke
  пакет был изолирован и повторяем.
- Актуализированы `docs/ARCHITECTURE_AUDIT_2026-04-08.md` и
  `docs/ARCH_UI_REFACTORING_ROADMAP_2026-04-15.md` под новый
  notification-routing continuity шаг.

## Проверка

- `spring-panel\.\mvnw.cmd "-Dtest=PublicFormFlowSmokeIntegrationTest" test`
- `spring-panel\.\mvnw.cmd "-Dtest=PublicFormFlowSmokeIntegrationTest,PublicFormApiContractServiceTest,PublicFormApiResponseServiceTest,PublicFormApiControllerWebMvcTest,PublicFormControllerWebMvcTest" test`

## Что дальше

- Следующим широким пакетом можно уже добирать adjacent notification
  transport/API слой отдельно от SQLite timestamp legacy, либо идти в
  deeper end-to-end continuity между `public-form`, `dialogs` list и
  operator activity projections.

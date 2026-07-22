# 2026-07-22 08:59:53 — dialog legacy question template audit

## Контекст
- Пользователь: `бери в работу следующий шаг по задаче`
- Значимый контекст из `01-150`: после cleanup root mirrors и migration-only shared settings next step сместился в финальный аудит remaining schema boundaries, прежде всего вокруг `dialog_config.question_templates` и historical recovery snapshots.

## Что сделано
- В `spring-panel/src/main/java/com/example/panel/controller/ManagementController.java` добавлен bootstrap audit для `dialog_config.question_templates`:
  - `/settings` теперь публикует `dialog.legacyQuestionTemplateAudit`;
  - audit явно помечает домен как `legacy_operator_workspace`;
  - в audit зафиксированы consumers (`settings-dialog-templates-runtime.js`, `dialogs.js`), bot path `bot_settings.question_templates` и то, что snapshot в `temp-recovery/routing-migration-backup-2026-07-08_085737/settings.json` не считается canonical contract.
- В `spring-panel/src/main/resources/templates/settings/index.html`, `spring-panel/src/main/resources/static/js/settings-page-config-runtime.js`, `spring-panel/src/main/resources/static/js/settings-page-bootstrap-runtime.js` и `spring-panel/src/main/resources/static/js/settings-dialog-templates-runtime.js` settings UI/runtime переведены на явное разделение доменов:
  - секция переименована в шаблоны операторских вопросов;
  - появился schema-audit блок, который показывает count, source path, consumers и separation from bot runtime;
  - bootstrap payload теперь содержит metadata для этого audit.
- В `spring-panel/src/main/resources/templates/dialogs/index.html` рабочий dialogs UI тоже переименован в шаблоны операторских вопросов, чтобы не смешивать operator/workspace templates с bot question templates.
- В `spring-panel/src/main/java/com/example/panel/service/SettingsDialogTemplateConfigService.java` сохранена отдельная legacy-ветка для `dialog_question_templates`, но теперь она сопровождается явным warning-log о том, что это operator workspace contract, а не bot runtime input.
- В `spring-panel/src/test/java/com/example/panel/controller/ManagementControllerWebMvcTest.java` и `spring-panel/src/test/java/com/example/panel/service/SettingsDialogTemplateConfigServiceTest.java` добавлены regression checks на bootstrap audit payload и сохранение operator question templates как отдельного dialog domain.
- В `ai-context/tasks/task-details/01-150.md` обновлены execution log, текущая точка остановки и следующий шаг.

## Проверки
- `spring-panel\\mvnw.cmd "-Dtest=ManagementControllerWebMvcTest,SettingsDialogTemplateConfigServiceTest" test`

## Следующий шаг
- Перейти к финальному removal-plan для remaining migration-only helpers и отдельно оформить решение по historical recovery snapshots как по архивным артефактам, а не по рабочему schema-contract.

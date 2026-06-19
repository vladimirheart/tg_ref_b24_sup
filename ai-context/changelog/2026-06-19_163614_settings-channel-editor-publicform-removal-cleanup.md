# 2026-06-19 16:36:14 - settings channel editor publicform removal cleanup

## Промпты пользователя

- `давай следующий шаг. и ещё: я публичные формы выпилил из проекта`

## Что изменено

- из `spring-panel/src/main/resources/templates/settings/index.html` удалены мёртвые inline-хвосты channel editor, связанные с удалённым public form функционалом: `publicform` DOM-константы, `publicFormFields` state, helper'ы редактора полей и старая инициализация `questions_cfg.fields`;
- `buildChannelQuestionsCfgPayload()` сокращён до реально живой части: теперь он сохраняет `panelNotifications` и больше не собирает удалённые public form поля/лимиты/response ETA;
- в `spring-panel/src/main/resources/static/js/settings-channel-editor-shell-runtime.js` удалены остатки `public_id/public link` и dead handlers `regenerate/copy public link`, а также лишняя привязка `newPublicAppeal` input, которого больше нет в markup;
- в `spring-panel/src/main/resources/static/js/settings-channel-editor-controls-runtime.js` убраны dead bindings под `public_id/public link`, а в `settings-channel-editor-persistence-runtime.js` / `settings-channels-catalog-runtime.js` подчищены связанные obsolete assumptions;
- в `ai-context/tasks/task-details/01-129.md` обновлён статус задачи: `publicform` больше не рассматривается как boundary этого этапа, а как удалённый функционал, хвосты которого были дочищены.

## Проверка

- `node --check spring-panel/src/main/resources/static/js/settings-channel-editor-shell-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-channel-editor-controls-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-channel-editor-persistence-runtime.js`
- `git diff --check -- spring-panel/src/main/resources/templates/settings/index.html spring-panel/src/main/resources/static/js/settings-channel-editor-shell-runtime.js spring-panel/src/main/resources/static/js/settings-channel-editor-controls-runtime.js spring-panel/src/main/resources/static/js/settings-channel-editor-persistence-runtime.js spring-panel/src/main/resources/static/js/settings-channels-catalog-runtime.js ai-context/tasks/task-details/01-129.md`
- `rg -n "public_id|public/forms|channelEditorPublicForm|publicFormFields|normalizePublicFormField|collectPublicFormFieldsFromEditor|renderPublicFormFieldsEditor|channelEditorPanelNotifNewPublicAppealInput|handleRegeneratePublicIdClick|handleCopyPublicLinkClick|buildPublicFormUrl" spring-panel/src/main/resources/templates/settings/index.html spring-panel/src/main/resources/static/js/settings-channel-editor-shell-runtime.js spring-panel/src/main/resources/static/js/settings-channel-editor-controls-runtime.js spring-panel/src/main/resources/static/js/settings-channel-editor-persistence-runtime.js spring-panel/src/main/resources/static/js/settings-channels-catalog-runtime.js`

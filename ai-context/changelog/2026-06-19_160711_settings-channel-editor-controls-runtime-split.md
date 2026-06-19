# 2026-06-19 16:07:11 - settings channel editor controls runtime split

## Промпты пользователя

- `давай следующий шаг`

## Что изменено

- добавлен новый bounded runtime `spring-panel/src/main/resources/static/js/settings-channel-editor-controls-runtime.js` для subdomain `channels / channel editor / controls bindings`;
- в новый runtime вынесены non-`publicform` listeners из `initChannelEditorControls()`: shell actions, test/bot controls, token actions и вызовы persistence runtime для `save/delete`;
- в `spring-panel/src/main/resources/templates/settings/index.html` `initChannelEditorControls()` упрощён до `settingsChannelEditorControls.bindControls()` и отдельной `publicform`-ветки добавления поля;
- в `ai-context/tasks/task-details/01-129.md` обновлён прогресс задачи: добавлен выполненный пакет `settings-channel-editor-controls-runtime.js`, а remaining channel-editor inline scope сведён к `publicform` boundary.

## Проверка

- `node --check spring-panel/src/main/resources/static/js/settings-channel-editor-controls-runtime.js`
- `git diff --check -- spring-panel/src/main/resources/templates/settings/index.html spring-panel/src/main/resources/static/js/settings-channel-editor-controls-runtime.js ai-context/tasks/task-details/01-129.md`
- `rg -n "channelEditorRegeneratePublicIdBtn\\?\\.addEventListener|channelEditorCopyPublicLinkBtn\\?\\.addEventListener|channelEditorQuestionSelect\\?\\.addEventListener|channelEditorRatingSelect\\?\\.addEventListener|channelEditorAutoSelect\\?\\.addEventListener|channelEditorSupportChatInput\\?\\.addEventListener|channelEditorSendTestBtn\\?\\.addEventListener|channelEditorBotStartBtn\\?\\.addEventListener|channelEditorBotStopBtn\\?\\.addEventListener|channelEditorBotRefreshBtn\\?\\.addEventListener|channelEditorToggleTokenBtn\\?\\.addEventListener|channelEditorCopyTokenBtn\\?\\.addEventListener|channelEditorSaveBtn\\?\\.addEventListener|channelEditorDeleteBtn\\?\\.addEventListener|settingsChannelEditorControls\\.bindControls" spring-panel/src/main/resources/templates/settings/index.html spring-panel/src/main/resources/static/js/settings-channel-editor-controls-runtime.js`

# 2026-06-19 16:02:07 - settings channel editor persistence runtime split

## Промпты пользователя

- `давай следующий шаг по задаче`

## Что изменено

- добавлен новый bounded runtime `spring-panel/src/main/resources/static/js/settings-channel-editor-persistence-runtime.js` для subdomain `channels / channel editor / persistence`;
- в новый runtime вынесены `saveChannel`, `removeChannel`, transport retry/fallback (`POST` → `PUT`), basic validation и non-`publicform` сборка payload для `delivery_settings`, `platform_config`, template ids и channel route;
- в `spring-panel/src/main/resources/templates/settings/index.html` `publicform`-специфичный слой оставлен отдельным callback `buildChannelQuestionsCfgPayload(existingQuestionsCfg)`, который передаётся в runtime как injected boundary;
- кнопки `Сохранить` и `Удалить` в `initChannelEditorControls()` переведены на `settingsChannelEditorPersistence.handleSaveClick()` / `handleDeleteClick()`;
- в `ai-context/tasks/task-details/01-129.md` обновлён прогресс задачи: добавлен выполненный пакет `settings-channel-editor-persistence-runtime.js`, а остаток сужен до `buildChannelQuestionsCfgPayload` и remaining editor wiring.

## Проверка

- `node --check spring-panel/src/main/resources/static/js/settings-channel-editor-persistence-runtime.js`
- `git diff --check -- spring-panel/src/main/resources/templates/settings/index.html spring-panel/src/main/resources/static/js/settings-channel-editor-persistence-runtime.js`
- `rg -n "async function saveChannel|async function removeChannel|buildChannelQuestionsCfgPayload|settingsChannelEditorPersistence|handleSaveClick|handleDeleteClick" spring-panel/src/main/resources/templates/settings/index.html spring-panel/src/main/resources/static/js/settings-channel-editor-persistence-runtime.js`

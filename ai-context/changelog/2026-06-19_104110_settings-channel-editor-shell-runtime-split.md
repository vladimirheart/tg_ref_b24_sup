# 2026-06-19 10:41:10 - settings channel editor shell runtime split

## Промпты пользователя

- `давай следующий шаг`

## Что изменено

- добавлен новый bounded runtime `spring-panel/src/main/resources/static/js/settings-channel-editor-shell-runtime.js` для subdomain `channels / channel editor / shell`;
- в новый runtime вынесены channel-editor shell helpers: status label, template summaries/select setup, support-chat hint, notification-routing state, public form link helper, editor trigger/reset hooks и публичные actions `regenerate/copy link`;
- `populateChannelEditor()` в `spring-panel/src/main/resources/templates/settings/index.html` разделён: `publicform`-часть осталась inline, а вся не-`publicform` shell-инициализация переведена на `populateChannelEditorShell(channel)`;
- listeners в `initChannelEditorControls()` для public-link actions, summary/select helpers, active toggle и support-chat hint переведены на `settingsChannelEditorShell`, без изменения `saveChannel` и без углубления в `publicform` field editor.

## Проверка

- `node --check spring-panel/src/main/resources/static/js/settings-channel-editor-shell-runtime.js`
- `git diff --check -- spring-panel/src/main/resources/templates/settings/index.html spring-panel/src/main/resources/static/js/settings-channel-editor-shell-runtime.js`
- `rg -n "function updateChannelEditorStatusLabel\(|function updateChannelEditorSummary\(|function configureChannelEditorSelect\(|function updateSupportChatHint\(|function updateChannelPanelNotificationRoutingState\(|function buildPublicFormUrl\(|function refreshChannelEditorIfOpen\(|window\.prepareChannelEditorSettingsTrigger|window\.resetChannelEditorSettingsModal" spring-panel/src/main/resources/templates/settings/index.html spring-panel/src/main/resources/static/js/settings-channel-editor-shell-runtime.js`

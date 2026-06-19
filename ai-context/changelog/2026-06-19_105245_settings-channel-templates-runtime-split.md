# 2026-06-19 10:52:45 - settings channel templates runtime split

## Промпты пользователя

- `давай следующий шаг`

## Что изменено

- добавлен новый bounded runtime `spring-panel/src/main/resources/static/js/settings-channel-templates-runtime.js` для subdomain `channels / templates / summary helpers`;
- в новый runtime вынесены template catalog helpers: question/rating/auto template lists и maps, default template ids, `sanitizeTemplateId`, `buildTemplateOptions` и summary builders для question/rating/auto templates;
- в `spring-panel/src/main/resources/templates/settings/index.html` подключён `settings-channel-templates-runtime.js`, а inline-блок `BOT_SETTINGS_DATA` / template maps / summary functions заменён на `mount + alias` к runtime;
- `settings-channels-catalog-runtime` и `settings-channel-editor-shell-runtime` переведены на lazy callbacks к `settingsChannelTemplates`, чтобы add-channel и channel-editor summary/select логика больше не зависели от giant inline helper-блока.

## Проверка

- `node --check spring-panel/src/main/resources/static/js/settings-channel-templates-runtime.js`
- `git diff --check -- spring-panel/src/main/resources/templates/settings/index.html spring-panel/src/main/resources/static/js/settings-channel-templates-runtime.js`
- `rg -n "function sanitizeTemplateId\(|function buildTemplateOptions\(|function getQuestionTemplateSummary\(|function getRatingTemplateSummary\(|function getAutoActionTemplateSummary\(|const TEMPLATE_SUMMARY_BUILDERS = \{|const BOT_SETTINGS_DATA =" spring-panel/src/main/resources/templates/settings/index.html spring-panel/src/main/resources/static/js/settings-channel-templates-runtime.js`

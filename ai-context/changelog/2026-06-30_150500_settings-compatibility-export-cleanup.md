# 2026-06-30 15:05:00 — settings compatibility export cleanup

## user prompt

> тогда забирай в работу: финально пройтись по оставшимся compatibility export/page-hook хвостам в settings, где ещё есть смысл либо оставить явный public API, либо убрать как мёртвую совместимость;

## what changed

- в `spring-panel/src/main/resources/static/js/bot-settings.js` удалён
  неиспользуемый compatibility-экспорт `window.BotSettingsBridge` вместе с
  внутренним snapshot/subscribe слоем, потому что после снятия старого
  event-bus у него не осталось потребителей в репозитории;
- в `spring-panel/src/main/resources/static/js/settings-page-init-runtime.js`
  снят наружный export `window.SettingsPageInitRuntime`: runtime продолжает
  auto-mount через `data-settings-page-init-payload`, а защита от повторной
  загрузки переведена на внутренний sentinel
  `window.__settingsPageInitRuntimeScriptLoaded`;
- в `ai-context/tasks/task-details/01-129.md` зафиксировано, какие page-level
  контракты остаются явным public API (`SettingsRuntimeAccess`,
  `SettingsPageBootstrapRuntime`, `SettingsPageCallbackRegistry`,
  `SettingsPageShell`), а какие compatibility-слои уже сняты как мёртвые.

## verification

- `rg -n "BotSettingsBridge|bridgeSubscribers|notifyBridgeSubscribers|setupBridge" spring-panel/src/main/resources/static/js/bot-settings.js -S`
- `rg -n "SettingsPageInitRuntime" spring-panel/src/main/resources/static/js spring-panel/src/main/resources/templates -S`
- `node --check spring-panel/src/main/resources/static/js/bot-settings.js`
- `node --check spring-panel/src/main/resources/static/js/settings-page-init-runtime.js`

# 2026-06-30 15:46:00 — settings multi-subdomain fallback map cleanup

## user prompt

> возьми несколько следующих шагов

## what changed

- в `spring-panel/src/main/resources/static/js/settings-page-shell.js` резко
  сужен `SETTINGS_RUNTIME_CALLBACK_TARGETS`: из него убраны дублирующие
  `user-action` и `modal-lifecycle` callback names для `appearance`,
  `locations`, `parameters`, `partner contacts`, `legal entities`,
  `network profiles`, `admin/reporting` и `channels`, если эти имена уже
  публикуются через `SettingsPageCallbackRegistry` соответствующими runtime
  после mount;
- в shell map оставлены только явные bootstrap/public API точки
  (`init*`, `load*`, `buildLocationsTree`, `renderLocationsIiko*`,
  `saveSettings`), где registry не должен становиться единственным fallback
  контрактом;
- в `ai-context/tasks/task-details/01-129.md` зафиксирован этот более широкий
  cleanup-пакет и уточнён следующий остаток: отдельно решать судьбу самих
  `window.Settings*Runtime` wrapper methods поверх `window.__settings*Runtime`.

## verification

- `node --check spring-panel/src/main/resources/static/js/settings-page-shell.js`
- `rg -n "prepareParameterSettingsTrigger|preparePartnerContactEditorSettingsTrigger|resetLocationsSettingsModal|startStatusesEdit|prepareAddChannelSettingsModal" spring-panel/src/main/resources/static/js/settings-page-shell.js -S`

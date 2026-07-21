# 2026-07-21 08:40:06 - settings page runtime bootstrap

- Затронутые области:
  - `spring-panel/src/main/resources/static/js/bot-settings.js`
  - `ai-context/tasks/task-details/01-150.md`
- Пользовательский промпт:
  - `бери в работу следующий крупный шаг`
- Что сделано:
  - `bot-settings.js` переведён на единственный bootstrap-источник `SettingsPageConfigRuntime`; fallback на legacy `BOT_SETTINGS_INITIAL` / `BOT_PRESET_DEFINITIONS` и связанный dual-bootstrap удалены;
  - diagnostic-поведение обновлено: success-состояние теперь явно говорит, что загрузка идёт через `SettingsPageConfigRuntime`, а не через временные globals;
  - `01-150` синхронизирован с текущим состоянием migration path: отмечено закрытие dual-bootstrap и перенесён следующий крупный шаг в cleanup channel compatibility (`questions_cfg`).
- Проверки:
  - `rg -n "BOT_SETTINGS_INITIAL|BOT_PRESET_DEFINITIONS|usesLegacyBootstrapGlobals" spring-panel/src/main/resources/static/js/bot-settings.js` — no matches
  - `node --check spring-panel/src/main/resources/static/js/bot-settings.js` — success
  - `spring-panel\\mvnw.cmd -DskipTests compile` — success

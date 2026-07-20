# 2026-07-20 17:02:04 - settings legacy ui audit

- Затронутые области:
  - `spring-panel/src/main/resources/templates/settings/index.html`
  - `spring-panel/src/main/resources/static/js/bot-settings.js`
  - `spring-panel/src/test/java/com/example/panel/controller/ManagementControllerWebMvcTest.java`
- Пользовательский промпт:
  - `давай следующий шаг по задаче`
- Что сделано:
  - на странице настроек добавлен отдельный UI-блок `Legacy-аудит bot settings`, который показывает состояние migration path после загрузки настроек;
  - `bot-settings.js` теперь отслеживает, были ли данные подняты из deprecated `bot_settings.question_flow`, `bot_settings.rating_system`, template-level `questions` или через legacy `BOT_*` globals, и показывает это в diagnostics;
  - для канонического payload UI явно сообщает, что bot settings загружены по схеме `question_templates / rating_templates` без deprecated fallback;
  - bootstrap-тест страницы настроек обновлён проверкой нового diagnostic placeholder.
- Проверки:
  - `node --check spring-panel/src/main/resources/static/js/bot-settings.js` — success
  - `spring-panel\\mvnw.cmd -DskipTests compile` — success

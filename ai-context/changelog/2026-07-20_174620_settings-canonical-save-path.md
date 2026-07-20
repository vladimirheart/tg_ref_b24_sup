# 2026-07-20 17:46:20 - settings canonical save path

- Затронутые области:
  - `spring-panel/src/main/resources/static/js/bot-settings.js`
  - `spring-panel/src/main/resources/static/js/settings-save-runtime.js`
  - `spring-panel/src/test/java/com/example/panel/service/SettingsUpdateSharedConfigIntegrationTest.java`
- Пользовательский промпт:
  - `хорошо, давай следующий крупный шаг по задаче`
- Что сделано:
  - bot settings editor перестал отправлять deprecated mirrors `question_flow` и `rating_system`; теперь в save payload остаются только канонические `question_templates`, `active_template_id`, `rating_templates` и `active_rating_template_id`;
  - общий settings save runtime перестал отправлять `auto_close_hours` как самостоятельное top-level поле и сохраняет только канонический `auto_close_config` с гарантированным `active_template_id`;
  - integration tests закрепили, что backend по-прежнему сам выводит derived `question_flow` и `auto_close_hours` при сохранении канонического payload без legacy mirrors.
- Проверки:
  - `node --check spring-panel/src/main/resources/static/js/bot-settings.js` — success
  - `node --check spring-panel/src/main/resources/static/js/settings-save-runtime.js` — success
  - `spring-panel\\mvnw.cmd -DskipTests compile` — success

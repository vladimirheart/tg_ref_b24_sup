# 2026-07-23 10:01:12 — question presets dynamic reload

- Затронутые файлы:
  - `spring-panel/src/main/java/com/example/panel/service/SettingsPageDataService.java`
  - `spring-panel/src/main/resources/static/js/bot-settings.js`
  - `spring-panel/src/test/java/com/example/panel/service/SettingsPageDataServiceTest.java`
  - `ai-context/tasks/task-details/01-150.md`

- Промпт пользователя:
  - `в шаблонах вопросов пишет: "Готовые поля сейчас недоступны. Добавьте структуру локаций / готовые поля в разделе «Структура локаций», чтобы использовать этот тип вопроса. "`
  - `почему, если структура локаций у меня формируется автоматически в "Настройки - Структура локаций"`

- Что сделано:
  - В `SettingsPageDataService` секция `/api/settings/page-data/channels` теперь возвращает `botPresetDefinitions`, собранные из актуального `effectiveLocationsPayload`, а не только network-поля.
  - В `bot-settings.js` добавлена ленивая догрузка preset definitions при открытии редактора шаблонов вопросов: если initial bootstrap пришёл пустым, UI запрашивает секцию `channels`, показывает промежуточный loading-state и после ответа пересобирает список готовых полей.
  - Добавлен `SettingsPageDataServiceTest`, который фиксирует новый контракт секции `channels` и проверяет, что dynamic payload включает `botPresetDefinitions`.
  - В `01-150` добавлена отдельная execution-note про закрытый drift между автоподгрузкой структуры локаций и доступностью готовых полей в шаблонах вопросов.

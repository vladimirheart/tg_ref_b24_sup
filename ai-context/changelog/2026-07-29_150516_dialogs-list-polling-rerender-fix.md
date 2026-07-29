# 2026-07-29 15:05:16 - dialogs list polling rerender fix

- Затронутые области:
  - `spring-panel/src/main/resources/static/js/dialogs-list-runtime.js`
  - `ai-context/tasks/task-details/01-152.md`
- Пользовательский промпт:
  - `уже лучше. на странице диалогов при открытии примерно через 8 секунд страница как будто перерисовывется`
- Что сделано:
  - в runtime списка диалогов добавлена функция `syncTableBodyOrder`, которая перед перестановкой строк сравнивает текущий и целевой порядок `tr`;
  - обновление списка диалогов больше не прогоняет все строки через `appendChild`, если порядок строк фактически не изменился;
  - тот же guard применён в `applyFilters`, чтобы повторное применение фильтров и пагинации не создавало лишний визуальный repaint таблицы;
  - polling, SLA-обновления, фильтры и существующая логика синхронизации списка сохранены без изменения контракта.
- Проверки:
  - `node --check spring-panel/src/main/resources/static/js/dialogs-list-runtime.js` — success
  - `./mvnw.cmd -q -DskipTests compile` в `spring-panel` — success

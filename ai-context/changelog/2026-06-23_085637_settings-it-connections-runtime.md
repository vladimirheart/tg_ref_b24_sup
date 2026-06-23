# 2026-06-23 08:56:37 - settings it connections runtime

## Промпты пользователя

- `давай следующий шаг`

## Что изменено

- продолжен `01-129`: создан `settings-it-connections-runtime.js`, в который
  вынесен subdomain `it_connection` вместе с accordion-рендерингом,
  draft/save/delete/restore flow, add-modal orchestration и созданием
  категорий подключений;
- `settings/index.html` переведён на mount внешнего runtime для
  `it_connection`: подключён новый script, inline `it_connection` block удалён,
  а оставшиеся перерисовки переведены на вызовы
  `settingsItConnectionsRuntime?.renderItConnectionsTable()`;
- карточка `ai-context/tasks/task-details/01-129.md` обновлена под текущий
  статус: зафиксирован пакет `it_connection`, уточнён оставшийся scope и
  отражено, что `publicform` больше не является отдельным активным subtrack.

## Проверка

- `node --check spring-panel/src/main/resources/static/js/settings-it-connections-runtime.js`
- `git diff --check -- spring-panel/src/main/resources/templates/settings/index.html spring-panel/src/main/resources/static/js/settings-it-connections-runtime.js ai-context/tasks/task-details/01-129.md ai-context/changelog/2026-06-23_085637_settings-it-connections-runtime.md`

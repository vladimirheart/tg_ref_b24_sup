# 2026-06-29 17:46:28 - settings page shell dead data overrides cleanup

## Промпты пользователя

- `давай следующий шаг по задаче`

## Что изменено

- `spring-panel/src/main/resources/static/js/settings-page-shell.js` очищен от
  неиспользуемого inline override-слоя для
  `data-settings-bootstrap`, `data-settings-url-*`, `data-settings-on*`,
  `data-settings-reset-tab`, `data-settings-focus-*`,
  `data-settings-query-*` и `data-settings-url-modal`;
- page shell теперь использует только `DEFAULT_*` карты и фактический settings
  template contract, без лишней поддержки неиспользуемых data-override
  сценариев;
- перед cleanup дополнительно проверено, что эти `data-settings-*` атрибуты в
  settings template больше не используются;
- `ai-context/tasks/task-details/01-129.md` обновлён: в задаче зафиксировано,
  что shell-side configurable override слой уже снят, а remaining scope ещё
  сильнее смещён к runtime namespace mount/orchestration и payload cleanup.

## Проверка

- `node --check spring-panel/src/main/resources/static/js/settings-page-shell.js`
- `git diff --check -- spring-panel/src/main/resources/static/js/settings-page-shell.js ai-context/tasks/task-details/01-129.md ai-context/changelog/2026-06-29_174628_settings-page-shell-dead-data-overrides-cleanup.md`

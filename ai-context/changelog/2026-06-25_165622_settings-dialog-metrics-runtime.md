# 2026-06-25 16:56:22 - settings dialog metrics runtime

## Промпты пользователя

- `давай следующий шаг по задаче`

## Что изменено

- продолжен `01-129`: создан `settings-dialog-metrics-runtime.js`, который
  забрал subdomain `time metrics / summary badges` вместе с нормализацией,
  preview/update логикой, status-badge editor и payload collection для
  сохранения настроек;
- `settings/index.html` переведён на mount нового dialog-metrics runtime:
  удалены inline helper-функции для time metrics и summary badges, а
  `saveSettings()` теперь получает оба payload через runtime API;
- карточка `01-129` обновлена: зафиксирован новый runtime-split пакет, а
  остаточный scope смещён на `SLA / workspace governance` и оставшиеся
  page-level bridge entry-points.

## Проверка

- `node --check spring-panel/src/main/resources/static/js/settings-dialog-metrics-runtime.js`
- `git diff --check -- spring-panel/src/main/resources/templates/settings/index.html spring-panel/src/main/resources/static/js/settings-dialog-metrics-runtime.js ai-context/tasks/task-details/01-129.md ai-context/changelog/2026-06-25_165622_settings-dialog-metrics-runtime.md`

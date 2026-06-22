# 2026-06-22 09:53:04 - settings it equipment runtime

## Промпты пользователя

- `давай следующий шаг`

## Что изменено

- продолжен `01-129`: создан отдельный runtime `settings-it-equipment-runtime.js`
  для bounded subdomain equipment catalog внутри settings;
- из `settings/index.html` вынесены inline-хелперы каталога оборудования:
  table render, links editor, загрузка списка, add modal и save/delete flow;
- `it_connection` inline-accordion сохранён на месте, но теперь делегирует
  перерисовку equipment table во внешний runtime и загружает equipment catalog
  через явный mount API.

## Затронутые файлы

- `spring-panel/src/main/resources/static/js/settings-it-equipment-runtime.js`
- `spring-panel/src/main/resources/templates/settings/index.html`
- `ai-context/tasks/task-details/01-129.md`

## Проверка

- `node --check spring-panel/src/main/resources/static/js/settings-it-equipment-runtime.js`
- `git diff --check -- spring-panel/src/main/resources/templates/settings/index.html spring-panel/src/main/resources/static/js/settings-it-equipment-runtime.js ai-context/tasks/task-details/01-129.md ai-context/changelog/2026-06-22_095304_settings-it-equipment-runtime.md`

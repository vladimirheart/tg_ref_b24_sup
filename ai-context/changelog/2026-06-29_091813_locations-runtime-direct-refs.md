# 2026-06-29 09:18:13 - locations runtime direct refs

## Промпты пользователя

- `давай следующий более широкий шаг по задаче 01-129`

## Что изменено

- `spring-panel/src/main/resources/static/js/settings-locations-tree-runtime.js`
  больше не зависит только от глобалов `window.serializeLocationsIikoServerSources`,
  `window.serializeLocationsIikoSyncSettings` и
  `window.markLocationsIikoServerSourcesSaved` в save-flow: эти hooks теперь
  можно прокидывать через mount options, с legacy fallback для совместимости;
- `spring-panel/src/main/resources/static/js/settings-page-bootstrap-runtime.js`
  переведён на прямые runtime references для связки
  `settings locations tree <-> settings locations iiko`: location wizard,
  admin shell location state, parameter-shell `buildLocationsTree` bridge и
  общий `/settings` save-flow теперь используют уже смонтированные runtime-объекты,
  а не прямые `window.*` wrappers;
- `ai-context/tasks/task-details/01-129.md` обновлён: в карточке задачи
  зафиксирован переход части locations cross-runtime wiring с legacy globals на
  прямые runtime references.

## Проверка

- `node --check spring-panel/src/main/resources/static/js/settings-page-bootstrap-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-locations-tree-runtime.js`
- `git diff --check -- spring-panel/src/main/resources/static/js/settings-page-bootstrap-runtime.js spring-panel/src/main/resources/static/js/settings-locations-tree-runtime.js ai-context/tasks/task-details/01-129.md`

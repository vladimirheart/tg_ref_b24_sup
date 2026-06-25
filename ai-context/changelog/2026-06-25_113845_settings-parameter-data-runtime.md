# 2026-06-25 11:38:45 - settings parameter data runtime

## Промпты пользователя

- `давай следующий шаг`

## Что изменено

- продолжен `01-129`: создан `settings-parameter-data-runtime.js`, в который
  вынесены shared helper-слой над `parameterData`, `syncParameterData`,
  dependency/category normalization и общий `it_connection` category state;
- `settings/index.html` переведён на mount нового shared runtime: generic
  wrappers теперь ходят через его API, `settings-it-connections-runtime` и
  `settings-parameters-runtime` получают dependency/category helpers через
  callbacks, а старые inline-дубли `normalizeItConnectionCategory` /
  `getItConnectionCategoryLabel` удалены;
- карточка `01-129` обновлена в стиле `01-128`: зафиксирован новый слой split,
  уточнён остаточный scope и отмечено, что главный хвост задачи теперь сместился
  с shared helper-логики на page-level orchestration в template.

## Проверка

- `node --check spring-panel/src/main/resources/static/js/settings-parameter-data-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-parameters-runtime.js`
- `git diff --check -- spring-panel/src/main/resources/templates/settings/index.html spring-panel/src/main/resources/static/js/settings-parameter-data-runtime.js spring-panel/src/main/resources/static/js/settings-parameters-runtime.js ai-context/tasks/task-details/01-129.md ai-context/changelog/2026-06-25_113845_settings-parameter-data-runtime.md`

# 2026-06-25 11:48:22 - settings parameters shell runtime

## Промпты пользователя

- `давай следующий шаг`

## Что изменено

- продолжен `01-129`: создан `settings-parameters-shell-runtime.js`, который
  забрал page-level orchestration для `parameters/legal entities/partner contacts/it_connection`,
  включая mount-цепочку связанных runtime, initial render и `loadParameters()`;
- `settings/index.html` переведён на mount нового shell runtime: из template
  убраны локальные `parameterData/parametersLoaded`, прямые wrappers над
  parameter runtimes и document-level bindings `legal entities`, а соседние
  `locations` runtime теперь получают `loadParameters/getParameterData/isParametersLoaded`
  через shell API;
- карточка `01-129` обновлена: добавлен новый shell runtime в выполненную часть,
  а остаточный scope смещён с parameter-subdomain на прочие page-level bridge
  entry-points внутри settings template.

## Проверка

- `node --check spring-panel/src/main/resources/static/js/settings-parameters-shell-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-parameter-data-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-parameters-runtime.js`
- `git diff --check -- spring-panel/src/main/resources/templates/settings/index.html spring-panel/src/main/resources/static/js/settings-parameters-shell-runtime.js spring-panel/src/main/resources/static/js/settings-parameter-data-runtime.js spring-panel/src/main/resources/static/js/settings-parameters-runtime.js ai-context/tasks/task-details/01-129.md ai-context/changelog/2026-06-25_114822_settings-parameters-shell-runtime.md`

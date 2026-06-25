# 2026-06-25 11:25:41 - settings parameters runtime

## Промпты пользователя

- `давай следующий шаг`

## Что изменено

- продолжен `01-129`: создан `settings-parameters-runtime.js`, в который
  вынесен generic runtime для `parameters` вместе с parameter cards,
  `remote_access` section, city card/modal, dependency-select orchestration,
  modal triggers и CRUD/listener flow для обычных параметров;
- `settings/index.html` переведён на mount внешнего `settingsParametersRuntime`:
  подключён новый script, generic parameter render/delete/restore bridge теперь
  идёт через runtime API, а основной inline-блок `parameters` и старые
  `prepareParameter.../resetParameter...` entry-points удалены из шаблона;
- при переходе сохранён compatibility bridge для соседних runtime:
  `legal entities`, `partner contacts` и `it_connection` продолжают работать
  через callbacks нового parameter runtime, а `01-129.md` обновлён под новый
  остаточный scope вокруг shared helpers и sync/glue-логики.

## Проверка

- `node --check spring-panel/src/main/resources/static/js/settings-parameters-runtime.js`
- `git diff --check -- spring-panel/src/main/resources/templates/settings/index.html spring-panel/src/main/resources/static/js/settings-parameters-runtime.js ai-context/tasks/task-details/01-129.md ai-context/changelog/2026-06-25_112541_settings-parameters-runtime.md`

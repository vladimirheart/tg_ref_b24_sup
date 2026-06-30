# 2026-06-30 10:17:37 - settings runtime config bundles

## Промпты пользователя

- `давай следующий более широкий шаг по задаче`

## Что изменено

- `spring-panel/src/main/resources/static/js/settings-parameters-shell-runtime.js`
  переведён на чтение bounded `config`-bundle для subdomain `parameters`:
  titles/dependencies/state maps/city config/IT/contact dictionaries теперь
  берутся прежде всего из `options.config`, а старые getter-hooks оставлены как
  transition fallback;
- `spring-panel/src/main/resources/static/js/settings-network-profiles-runtime.js`
  тоже научен читать `parameters`-bundle напрямую через `config.networkProfiles`
  и `config.contractUsageData`, чтобы page bootstrap больше не прокидывал их
  отдельными функциями;
- `spring-panel/src/main/resources/static/js/settings-appearance-runtime.js`,
  `spring-panel/src/main/resources/static/js/settings-locations-iiko-runtime.js`
  и `spring-panel/src/main/resources/static/js/settings-locations-tree-runtime.js`
  переведены на bundled config-приём для `appearance` и `locations`, сохранив
  старые `options.*` поля как fallback на переходный период;
- `spring-panel/src/main/resources/static/js/settings-channels-shell-runtime.js`
  и `spring-panel/src/main/resources/static/js/settings-admin-shell-runtime.js`
  теперь тоже принимают subdomain config-объекты напрямую, включая
  `botSettings/autoClose/integrationNetwork*` и
  `reporting/managerLocationBindings`;
- `spring-panel/src/main/resources/static/js/settings-page-bootstrap-runtime.js`
  упрощён: mount contracts для `parameters`, `network profiles`, `appearance`,
  `locations`, `channels` и `admin` схлопнуты до bundle-передачи вместо
  длинной россыпи field-level параметров;
- `ai-context/tasks/task-details/01-129.md` обновлён: зафиксировано, что
  bounded bundle-contract уже дотянут не только до bootstrap, но и до
  downstream shell/runtime consumer-слоя.

## Проверка

- `node --check spring-panel/src/main/resources/static/js/settings-admin-shell-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-channels-shell-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-appearance-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-locations-iiko-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-locations-tree-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-network-profiles-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-parameters-shell-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-page-bootstrap-runtime.js`
- `git diff --check -- spring-panel/src/main/resources/static/js/settings-admin-shell-runtime.js spring-panel/src/main/resources/static/js/settings-channels-shell-runtime.js spring-panel/src/main/resources/static/js/settings-appearance-runtime.js spring-panel/src/main/resources/static/js/settings-locations-iiko-runtime.js spring-panel/src/main/resources/static/js/settings-locations-tree-runtime.js spring-panel/src/main/resources/static/js/settings-network-profiles-runtime.js spring-panel/src/main/resources/static/js/settings-parameters-shell-runtime.js spring-panel/src/main/resources/static/js/settings-page-bootstrap-runtime.js ai-context/tasks/task-details/01-129.md ai-context/changelog/2026-06-30_101737_settings-runtime-config-bundles.md`

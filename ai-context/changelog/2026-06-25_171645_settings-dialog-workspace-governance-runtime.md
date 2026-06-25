# 2026-06-25 17:16:45 - settings dialog workspace governance runtime

## Промпты пользователя

- `давай следующий шаг по задаче`

## Что изменено

- продолжен `01-129`: создан `settings-dialog-workspace-governance-runtime.js`,
  который забрал subdomain `workspace governance` вместе с hydration,
  validation, UTC timestamp presentation/summary и payload collection для
  сохранения настроек;
- `settings/index.html` переведён на mount нового governance runtime:
  удалены inline governance-блоки из `initDialogSlaControls()` и
  `collectDialogSlaConfig()`, а сохранение теперь получает governance payload
  через runtime API;
- карточка `01-129` обновлена: зафиксирован новый runtime-split пакет, а
  остаточный scope смещён на `SLA core / workspace rollout / external KPI`
  и оставшиеся page-level bridge entry-points.

## Проверка

- `node --check spring-panel/src/main/resources/static/js/settings-dialog-workspace-governance-runtime.js`
- `git diff --check -- spring-panel/src/main/resources/templates/settings/index.html spring-panel/src/main/resources/static/js/settings-dialog-workspace-governance-runtime.js ai-context/tasks/task-details/01-129.md ai-context/changelog/2026-06-25_171645_settings-dialog-workspace-governance-runtime.md`

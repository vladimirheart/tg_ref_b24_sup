# 2026-06-23 13:28:29 - settings network profiles runtime

## Промпты пользователя

- `давай следующий шаг`

## Что изменено

- продолжен `01-129`: создан `settings-network-profiles-runtime.js`, в который
  вынесен subdomain `network profiles` вместе с table render, profile editor
  modal, restaurant-id inputs, section-save flow и payload serialization;
- `settings/index.html` переведён на mount внешнего runtime для
  `network profiles`: подключён новый script, удалён inline runtime block, а
  общее сохранение `/settings` теперь берёт `network_profiles` через
  `settingsNetworkProfilesRuntime?.collectNetworkProfilesPayload()`;
- карточка `ai-context/tasks/task-details/01-129.md` обновлена под текущий
  статус: зафиксирован пакет `network profiles`, а остаточный scope сужен до
  `parameters / partner contacts` и общих shared helpers.

## Проверка

- `node --check spring-panel/src/main/resources/static/js/settings-network-profiles-runtime.js`
- `git diff --check -- spring-panel/src/main/resources/templates/settings/index.html spring-panel/src/main/resources/static/js/settings-network-profiles-runtime.js ai-context/tasks/task-details/01-129.md ai-context/changelog/2026-06-23_132829_settings-network-profiles-runtime.md`

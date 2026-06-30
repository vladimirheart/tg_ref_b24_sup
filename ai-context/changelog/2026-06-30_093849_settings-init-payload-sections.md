# 2026-06-30 09:38:49 - settings init payload sections

## Промпты пользователя

- `давай следующий более широкий шаг по задаче`

## Что изменено

- `spring-panel/src/main/resources/templates/settings/index.html` —
  `settingsPageInitPayload` переведён с плоского набора top-level ключей на
  секционный JSON contract: `dialog`, `admin`, `channels`, `parameters`,
  `appearance`, `locations`;
- `spring-panel/src/main/resources/static/js/settings-page-config-runtime.js`
  научен читать этот новый секционный payload через
  `resolveConfigSection(...)` / `resolveConfigValue(...)` и собирать прежний
  итоговый runtime config без изменения downstream runtime API;
- в `settings-page-config-runtime.js` сохранён fallback на старые flat keys,
  чтобы переход на новый payload contract оставался безопасным для соседних
  runtime и возможных промежуточных состояний шаблона;
- `ai-context/tasks/task-details/01-129.md` обновлён: в задаче зафиксировано,
  что remaining scope теперь ещё меньше про общий init payload shape и больше
  про точечные legacy/data хвосты по subdomain.

## Проверка

- `node --check spring-panel/src/main/resources/static/js/settings-page-config-runtime.js`
- `git diff --check -- spring-panel/src/main/resources/static/js/settings-page-config-runtime.js spring-panel/src/main/resources/templates/settings/index.html`
- `Select-String -Path 'spring-panel/src/main/resources/templates/settings/index.html' -Pattern '"dialog"','"admin"','"channels"','"parameters"','"appearance"','"locations"'`

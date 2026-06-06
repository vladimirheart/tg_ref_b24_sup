# 2026-06-06 10:22:33 - settings page shell explicit hooks

## Промпты пользователя

- `дальше`

## Что изменено

- продолжен `01-128`: `settings-page-shell.js` больше не опирается на class-based fallback
  для sheet modal discovery и использует только явные `data-settings-sheet` hooks;
- URL-open bootstrap страницы настроек переведён с жёсткого хардкода `channels/users`
  на декларативный поиск по `data-settings-url-modal`, чтобы page-shell runtime был
  связан с markup через `data-*`, а не через ручные ветвления по id;
- для primary settings-модалок в `settings/index.html` добавлены явные URL hooks:
  `users`, `channels`, `reporting`, `manager-bindings`, `locations`, `parameters`,
  `legal-entities`, `it-connections`, `appearance`, `dialogs`, `input-formatting`.

## Проверка

- `C:\Program Files\nodejs\node.exe --check spring-panel/src/main/resources/static/js/settings-page-shell.js`
- `git diff --check -- spring-panel/src/main/resources/templates/settings/index.html spring-panel/src/main/resources/static/js/settings-page-shell.js ai-context/changelog/2026-06-06_102233_settings-page-shell-explicit-hooks.md`

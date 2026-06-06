# 2026-06-06 10:12:03 - settings page shell auth bootstrap

## Промпты пользователя

- `продолжай.`
- `node.js я установил - должно стать легче`

## Что изменено

- продолжен `01-128`: bootstrap `usersModal` для lazy-mount/reset модуля `AuthManagement`
  перенесён из giant inline script `settings/index.html` в `settings-page-shell.js`;
- в новом shell runtime теперь живёт не только modal/tile/theme glue, но и page-entry клей
  между `usersModal` и внешним модулем `auth-management.js`, без смешения с предметной логикой
  ролей, пользователей и оргструктуры;
- giant inline script страницы настроек дополнительно уменьшен ещё на один `DOMContentLoaded`
  блок и стал чуть ближе к роли data/bootstrap host, а не page-shell orchestrator.

## Проверка

- `C:\Program Files\nodejs\node.exe --check spring-panel/src/main/resources/static/js/settings-page-shell.js`
- `git diff --check -- spring-panel/src/main/resources/templates/settings/index.html spring-panel/src/main/resources/static/js/settings-page-shell.js ai-context/changelog/2026-06-06_101203_settings-page-shell-auth-bootstrap.md`

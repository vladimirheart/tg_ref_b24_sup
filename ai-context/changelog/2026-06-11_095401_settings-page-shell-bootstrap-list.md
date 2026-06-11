# 2026-06-11 09:54:01 - settings page shell bootstrap list

## Промпты пользователя

- `давай дальше`
- `продолжи`

## Что изменено

- продолжен `01-128`: список domain bootstrap entrypoint'ов страницы настроек вынесен из
  жёстко прошитого массива внутри `settings-page-shell.js` в декларативный атрибут
  `data-settings-bootstrap` на корневом контейнере `data-settings-page-shell`;
- `runSettingsDomainBootstrap()` в `settings-page-shell.js` теперь читает и исполняет
  bootstrap-функции из markup, а не хранит их прямо внутри shell runtime;
- `settings/index.html` получил явный список settings bootstrap entrypoint'ов рядом
  с page-shell root, что делает следующий split subdomain-init ближе к markup-driven модели;
- текущий shell больше не требует knowledge о полном перечне domain init-функций внутри
  собственного кода, а держит только generic orchestration.

## Проверка

- `C:\Program Files\nodejs\node.exe --check spring-panel/src/main/resources/static/js/settings-page-shell.js`
- `git diff --check -- spring-panel/src/main/resources/templates/settings/index.html spring-panel/src/main/resources/static/js/settings-page-shell.js ai-context/changelog/2026-06-11_095401_settings-page-shell-bootstrap-list.md`

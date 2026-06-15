# 2026-06-15 16:38:58 - settings page shell default bootstrap

## Промпты пользователя

- `давай дальше`

## Что изменено

- в `spring-panel/src/main/resources/static/js/settings-page-shell.js` добавлен `DEFAULT_SETTINGS_BOOTSTRAP_FUNCTIONS` со штатным списком init/render bootstrap-функций страницы настроек;
- `runSettingsDomainBootstrap()` теперь использует список из runtime по умолчанию и читает `data-settings-bootstrap` только как override, а не как обязательный giant-атрибут;
- из root-контейнера в `spring-panel/src/main/resources/templates/settings/index.html` удалён длинный `data-settings-bootstrap="..."`, поэтому shell orchestration ещё меньше живёт в template markup;
- блоки `publicform` и `questions_cfg` не менялись.

## Проверка

- `node --check spring-panel/src/main/resources/static/js/settings-page-shell.js`
- `Select-String -Path 'spring-panel/src/main/resources/static/js/settings-page-shell.js' -Pattern 'DEFAULT_SETTINGS_BOOTSTRAP_FUNCTIONS|runSettingsDomainBootstrap'`
- `Select-String -Path 'spring-panel/src/main/resources/templates/settings/index.html' -Pattern 'data-settings-bootstrap='`
- `git diff --check -- spring-panel/src/main/resources/static/js/settings-page-shell.js spring-panel/src/main/resources/templates/settings/index.html`

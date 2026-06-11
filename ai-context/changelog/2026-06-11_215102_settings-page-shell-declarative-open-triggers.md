# 2026-06-11 21:51:02 - settings page shell declarative open triggers

## Промпты пользователя

- `погнали дальше`

## Что изменено

- продолжен `01-128` пакетом вокруг declarative modal-open flow: кнопка добавления записи в блоке локаций больше не использует inline `onclick`, а открывает `locationWizardModal` через `data-settings-open-modal` + `data-settings-action-callback`;
- подготовка location wizard вынесена в `prepareLocationWizardSettingsTrigger()`, поэтому shell сначала собирает состояние wizard, а уже потом открывает модалку;
- partner contact summary-card и fullscreen-кнопка тоже переведены на `data-settings-open-modal="partnerContactEditorModal"` + `preparePartnerContactEditorSettingsTrigger`;
- старый отдельный click/keydown wiring для `data-partner-contact-open-modal` удалён из giant inline script, а keyboard activation для non-button modal trigger’ов теперь поддерживается прямо в `settings-page-shell.js`.

## Проверка

- `C:\Program Files\nodejs\node.exe --check spring-panel/src/main/resources/static/js/settings-page-shell.js`
- `git diff --check -- spring-panel/src/main/resources/static/js/settings-page-shell.js spring-panel/src/main/resources/templates/settings/index.html`

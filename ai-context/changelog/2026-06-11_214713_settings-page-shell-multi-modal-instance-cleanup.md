# 2026-06-11 21:47:13 - settings page shell multi modal instance cleanup

## Промпты пользователя

- `продолжай пакетом шире`

## Что изменено

- продолжен `01-128` более широким cleanup-пакетом: из giant inline script убраны cached bootstrap modal instances для `partnerContactEditorModal`, `itConnectionAddModal` и `itEquipmentAddModal`;
- сценарии открытия и закрытия partner contact editor теперь опираются на `partnerContactEditorModalEl` и прямые вызовы `window.SettingsPageShell.showModal(...)` / `hideModal(...)`;
- закрытие модалок добавления IT-подключения и IT-оборудования после успешного сохранения тоже переведено на прямой shell API;
- `locationWizardModal` перестал храниться как bootstrap instance: wizard теперь держит только `locationWizardModalEl`, а открытие/закрытие идёт через `SettingsPageShell.showModal(...)` / `hideModal(...)`.

## Проверка

- `git diff --check -- spring-panel/src/main/resources/templates/settings/index.html`

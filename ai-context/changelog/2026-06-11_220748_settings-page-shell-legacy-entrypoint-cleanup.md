# 2026-06-11 22:07:48 - settings page shell legacy entrypoint cleanup

## Промпты пользователя

- `давай дальше`

## Что изменено

- продолжен `01-128` cleanup-пакетом после declarative migration: из `settings/index.html` удалён legacy entrypoint `window.openAddLocationWizard`, потому что открытие wizard уже идёт через `data-settings-open-modal` и `prepareLocationWizardSettingsTrigger()`;
- отдельная обёртка `openPartnerContactEditor()` тоже убрана: внутренний flow открытия partner contact editor теперь использует общий `preparePartnerContactEditor()` и прямой `SettingsPageShell.showModal(...)`;
- giant inline script стал чуть тоньше и меньше держит устаревшие shell-entrypoint’ы, которые больше не нужны после перехода на declarative trigger contract.

## Проверка

- `git diff --check -- spring-panel/src/main/resources/templates/settings/index.html`

# 2026-06-11 22:15:34 - settings page shell profile editor triggers

## Промпты пользователя

- `погнали дальше`

## Что изменено

- продолжен `01-128` пакетом вокруг profile editor open-flow: кнопка добавления и кнопки `Открыть` для `networkProfileEditorModal` переведены на `data-settings-open-modal` + `prepareNetworkProfileSettingsTrigger`;
- аналогично кнопка добавления и кнопки `Открыть` для `integrationNetworkProfileEditorModal` теперь идут через `data-settings-open-modal` + `prepareIntegrationNetworkProfileSettingsTrigger`;
- старые listener-ветки giant inline script, которые вручную вызывали `openNetworkProfileModal(...)` и `openIntegrationNetworkProfileEditor(...)`, удалены;
- после миграции убраны и мёртвые ссылки `networkProfilesAddButton` / `integrationNetworkProfilesAddBtn`.

## Проверка

- `git diff --check -- spring-panel/src/main/resources/templates/settings/index.html`

# 2026-06-11 22:23:04 - settings page shell parameter and quick add triggers

## Промпты пользователя

- `погнали дальше`

## Что изменено

- продолжен `01-128`: кнопки `+ Добавить контакт` в блоках партнёрских контактов переведены на `data-settings-open-modal` с callback `preparePartnerContactDraftSettingsTrigger`, чтобы shell забирал modal entrypoint, а giant inline script больше не держал отдельную click-ветку для этого открытия;
- `parameter-card` и city-card переведены на shell-trigger контракт: `data-param-toggle` и `data-city-toggle` теперь используют `data-settings-open-modal="parameterItemsModal"` и callback-prepare функции, которые либо готовят модалку, либо оставляют прежнее inline-поведение без открытия модального окна;
- `openParameterModal(...)` заменён на `prepareParameterModal(...)`, который только подготавливает состояние модалки, а само открытие теперь делегируется `settings-page-shell`;
- quick-add кнопка `Новый профиль` в карточке каналов теперь открывает `integrationNetworkProfileEditorModal` декларативно через shell и больше не требует отдельного JS-listener;
- после миграции удалены соответствующие ручные click/listener ветки и мёртвая ссылка `integrationNetworkProfilesQuickAddBtn`.

## Проверка

- `git diff --check -- spring-panel/src/main/resources/templates/settings/index.html`
- `rg -n "openParameterModal\\(|integrationNetworkProfilesQuickAddBtn|const integrationNetworkProfilesQuickAddBtn" spring-panel/src/main/resources/templates/settings/index.html`

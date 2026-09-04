# Theme-aware disclosure cards for iikoServer sources

## Пользовательский запрос

> вот теперь ок.
>
> ты пропустил первую часть моего сообщения, про свёрнутые плашки подключений у iikoserver.
> посмотри на скрин в этом сообщении. исправь в соответствии с правилами css-файлов

## Что изменено

- Белый фон disclosure-карточек устранён без правок generated CSS.
- Bootstrap `bg-body/text-body/text-bg-*` заменены semantic runtime classes.
- Surface/header/toggle/meta/actions/body оформлены через Iguana theme tokens.
- Enabled/disabled/warning badges переведены на `--state-*` / `--surface-*`.
- Empty-state источников также переведён на theme-aware presentation.
- Presentation добавлен только в `settings/_calm.scss`.
- Backend, shared-config, save/sync contract и collapse-state не менялись.
- 01-250 переведена в green после ручного подтверждения пользователя.
- Добавлен targeted source-contract test.

## Проверки

- `node --check spring-panel/src/main/resources/static/js/settings-locations-iiko-runtime.js`
- `git diff --check`
- Docker Maven: `SettingsLocationsIikoSourceThemeSurfaceContractTest`
- SHA guard: generated `app.css/settings.css` не меняются targeted test.

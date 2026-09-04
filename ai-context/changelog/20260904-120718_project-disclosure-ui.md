# Project-wide disclosure UI

## Пользовательский запрос

> мне очень нравится как разворачиваются карточки подключений, но в остальном проекте как-то криво и неприятно - давай по всему проекту подобные раскрытия сделаем как и у источников iikoserver

## Что изменено

- iikoServer disclosure language вынесен в canonical project-wide pattern.
- Bootstrap accordions получили единые theme-aware surfaces, borders, radii, hover и expanded state.
- Card-header collapse получил тот же header/chevron language.
- Runtime native details помечены ui-disclosure-native, кроме chat media popup menu.
- Добавлена плавная native-details animation с reduced-motion fallback.
- Settings IT/channels получили last-cascade compatibility partial.
- Generated CSS напрямую не редактируется.

## Проверки

- node --check для ui-disclosure.js и изменённых runtime JS.
- git diff --check.
- Docker Maven: ProjectDisclosureUiSourceContractTest.
- SHA guard: app.css/settings.css/style.css не меняются targeted test.

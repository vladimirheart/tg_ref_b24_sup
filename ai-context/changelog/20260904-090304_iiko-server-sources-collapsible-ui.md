# iikoServer sources collapsible UI

## Пользовательский запрос

> давай доработаем UI в этой-же вкладке. "Источники iikoServer API" - источники постоянно раскрыты и создают много шума на странице

## Что изменено

- Сохранённые `iikoServer API` источники переведены в компактные disclosure-карточки.
- В заголовке карточки видны чейн, URL, логин, enabled-статус и состояние секрета.
- Полная форма редактирования скрыта в Bootstrap collapse и раскрывается по клику.
- Новый источник автоматически открывается после добавления.
- Раскрытое состояние сохраняется при rerender во время редактирования.
- При закрытии settings-модалки disclosure-state очищается, поэтому при следующем
  открытии список снова компактный.
- Backend/save/sync contract не менялся.
- Добавлен targeted source-contract test.

## Проверки

- `node --check spring-panel/src/main/resources/static/js/settings-locations-iiko-runtime.js`
- `git diff --check`
- Docker Maven: `SettingsLocationsIikoSourceDisclosureSourceContractTest`

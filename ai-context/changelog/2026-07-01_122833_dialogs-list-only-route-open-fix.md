# 2026-07-01 12:28:33 - dialogs list-only route open fix

## Prompt

`поведение осталось таким-же, хотя проект полностью перезапускал`

## Что сделано

- Локализован browser-side источник симптома, который не сбрасывается перезапуском backend: `dialog-list-only-mode` хранится в `localStorage` и скрывает `workspace shell` через `.dialogs-extra-section`.
- При любом `openDialogSurface(...)` страница теперь автоматически выходит из `list-only mode`, чтобы открытый диалог не оставался невидимым после смены URL или route-autoload.
- И server-rendered, и client-rendered кнопки `Открыть` теперь формируют route с `channelId`, чтобы fallback-навигация без JS-интерсепта вела на полный dialog route.
- Повторно прогнан `DialogsControllerWebMvcTest` после правок `dialogs.js` и `dialogs/index.html`.

## Затронутые файлы

- `spring-panel/src/main/resources/static/js/dialogs.js`
- `spring-panel/src/main/resources/templates/dialogs/index.html`

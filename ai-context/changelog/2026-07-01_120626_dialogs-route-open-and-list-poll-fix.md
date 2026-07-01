# 2026-07-01 12:06:26 - dialogs route open and list poll fix

## Prompt

`при открытии страницы диалогов, спустя примерно 6 секунд страница перерисовывается. изменения почти не видны - список становится чуть плотнее. что-то вызывается дополнительно после загрузки страницы, что это и зачем?

и дополнительно: при попытке открыть диалог, урл меняется, но сам диалог не открывается`

## Что сделано

- В `dialogs` route-open сохранён `channelId` из query string: fallback-автооткрытие шаблона и runtime `openDialogDetailsByTicketId` теперь передают его дальше в workspace/details open flow.
- Для route/open и `popstate` добавлен точный поиск строки по паре `ticketId + channelId`, чтобы после перехода на `/dialogs/{ticketId}?channelId=...` открывался именно тот диалог, который был выбран.
- У server-rendered строк списка добавлен стабильный `data-dialog-marker`, совпадающий с клиентским marker-контрактом, поэтому первый list poll больше не считает весь SSR-список “изменившимся” и не пересобирает таблицу без фактических изменений.
- Прогнан `DialogsControllerWebMvcTest` для проверки server-side рендера страницы после добавления нового marker-поля.

## Затронутые файлы

- `spring-panel/src/main/java/com/example/panel/model/dialog/DialogListItem.java`
- `spring-panel/src/main/resources/static/js/dialogs.js`
- `spring-panel/src/main/resources/templates/dialogs/index.html`
